import chisel3._
import chisel3.util._
import chisel3.stage.ChiselStage

import Control._
import Helpers._
import LoadStoreByteReverse._

class LoadStoreInput(val bits: Int) extends Bundle {
  val a           = Input(UInt(bits.W))
  val b           = Input(UInt(bits.W))
  val data        = Input(UInt(bits.W))
  val internalOp  = Input(UInt(1.W))
  val length      = Input(UInt(2.W))
  val signed      = Input(UInt(1.W))
  val byteReverse = Input(UInt(1.W))
  val update      = Input(UInt(1.W))
  val reservation = Input(UInt(1.W))
}

/*
 * Simple non pipelined load/store unit
 *
 * if load:
 *   1C: a+b, load request
 *   2C: load data comes back, de align, byte swap or sign extend data
 *   3C: return data
 * 
 * if store:
 *   1C: a+b
 *       byte swap or sign extend, align data
 *   2C: form store mask, store data
 */

class LoadStore(val bits: Int, val words: Int) extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(Valid(new LoadStoreInput(bits)))
    val out = Output(Valid(UInt(bits.W)))
    val mem = new MemoryPort(bits, words, true)
    val tx = Output(UInt(1.W))
    val rx = Input(UInt(1.W))
  })

  io.out.valid := false.B
  io.out.bits := 0.U

  io.mem.addr := 0.U
  io.mem.writeMask := 0.U
  io.mem.writeData := 0.U
  io.mem.writeEnable := false.B

  val addr = Reg(UInt(bits.W))
  val data = Reg(UInt(bits.W))
  val length = Reg(UInt(2.W))
  val signed = Reg(UInt(1.W))
  val byteReverse = Reg(UInt(1.W))
  val reservation = Reg(UInt(1.W))
  val sIdle :: sStoreAccess :: sStoreIdle :: sLoadFormat :: sLoadReturn :: Nil = Enum(5)
  val state = RegInit(sIdle)

  val fifoLength = 128
  val rxOverclock = 16
  val uart = Module(new Uart(fifoLength, rxOverclock))

  io.tx := uart.io.tx
  uart.io.rx := io.rx

  uart.io.txQueue.valid := false.B
  uart.io.txQueue.bits := 0.U
  uart.io.rxQueue.ready := false.B

  val clockDivisor = RegInit(0.U(8.W))
  uart.io.clockDivisor := clockDivisor

  switch (state) {
    is (sIdle) {
      when (io.in.valid) {
        val a = io.in.bits.a + io.in.bits.b
        val offset = a(log2Ceil(bits/8)-1, 0)*8.U

        addr := a
        length := io.in.bits.length
        signed := io.in.bits.signed
        byteReverse := io.in.bits.byteReverse
        reservation := io.in.bits.reservation

        when (io.in.bits.internalOp === LDST_STORE) {
          /* Byte swap or sign extend data. We never do both. */
          when (io.in.bits.signed.asBool) {
            data := io.in.bits.data.signExtend(io.in.bits.length) << offset
          } .elsewhen (io.in.bits.byteReverse.asBool) {
            data := LoadStoreByteReverse(io.in.bits.data, io.in.bits.length) << offset
          } .otherwise {
            data := io.in.bits.data << offset
          }
          state := sStoreAccess
        } .otherwise {
          io.mem.addr := a >> log2Ceil(bits/8).U
          state := sLoadFormat
        }
      }
    }

    is (sStoreAccess) {
      val offset = addr(log2Ceil(bits/8)-1, 0)
      val lookupTable = Seq(LEN_1B -> "h1".U, LEN_2B -> "h3".U, LEN_4B -> "hf".U, LEN_8B -> "hff".U)
      val mask = Wire(UInt((bits/8).W))
      mask := MuxLookup(length, lookupTable.head._2, lookupTable) << offset

      /* UART */
      when (addr(31, 8) === "hc00020".U) {
        when (addr(7, 0) === "h00".U) {
          /* TX */
          uart.io.txQueue.valid := true.B
          uart.io.txQueue.bits := data(7, 0)
        } .elsewhen (addr === "hc0002018".U) {
          /* clock divisor */
          clockDivisor := data(7, 0)
        } .otherwise {
          /* ERROR */
        }
      } .otherwise {
        /* memory */
        io.mem.addr := addr >> log2Ceil(bits/8).U
        io.mem.writeEnable := true.B
        io.mem.writeMask := mask
        io.mem.writeData := data
      }

      /* Done, wait another cycle to line up with writeback */
      state := sStoreIdle
    }

    is (sStoreIdle) {
      /* Done */
      io.out.valid := true.B
      state := sIdle
    }

    is (sLoadFormat) {
       val offset = addr(log2Ceil(bits/8)-1, 0)*8.U
       val d = io.mem.readData >> offset

      /* Byte swap or sign extend data. We never do both. */
      when (signed.asBool) {
        data := d.signExtend(length)
      } .elsewhen (byteReverse.asBool) {
        data := LoadStoreByteReverse(d, length)
      } .otherwise {
        data := d.zeroExtend(length)
      }

      /* Syscon */
      when (addr(31, 8) === "hc00000".U) {
        when (addr(7, 0) === "h00".U) {
          /* SYS_REG_SIGNATURE */
          data := "hf00daa5500010001".U
        }
        when (addr(7, 0) === "h08".U) {
          /*
           * SYS_REG_INFO
           *  SYS_REG_INFO_HAS_UART is true
           *  Other bits are false
           */
          data := "h1".U
        }
        when (addr(7, 0) === "h20".U) {
          /* SYS_REG_CLKINFO */
          data := 50000000.U
        }
      }

      /* UART */
      when (addr(31, 8) === "hc00020".U) {
        when (addr(7, 0) === "h08".U) {
          /* RX */
          when (uart.io.rxQueue.valid) {
            uart.io.rxQueue.ready := true.B
            data := uart.io.rxQueue.bits
          }
        } .elsewhen (addr(7, 0) === "h10".U) {
          /* Status */
          data := uart.io.txFull ## uart.io.rxFull ## uart.io.txEmpty ## uart.io.rxEmpty
        } .elsewhen (addr(7, 0) === "h18".U) {
          /* clock divisor */
          data := clockDivisor
        } .otherwise {
          /* ERROR */
          data := 0.U
        }
      }

      state := sLoadReturn
    }

    is (sLoadReturn) {
      io.out.bits := data

      /* Done */
      io.out.valid := true.B
      state := sIdle
    }
  }
}

class LoadStoreWrapper(val bits: Int, val size: Int, filename: String) extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(Valid(new LoadStoreInput(bits)))
    val out = Output(Valid(UInt(bits.W)))
  })

  val loadStore = Module(new LoadStore(bits, size/(bits/8)))
  val mem = Module(new MemoryBlackBoxWrapper(bits, size/(bits/8), filename))

  io.in <> loadStore.io.in
  io.out <> loadStore.io.out
  loadStore.io.mem <> mem.io.loadStorePort

  loadStore.io.rx := loadStore.io.tx

  /* Fetch port is unused */
  mem.io.fetchPort.writeData := 0.U
  mem.io.fetchPort.writeEnable := false.B
  mem.io.fetchPort.addr := 0.U
  mem.io.fetchPort.writeMask := 0.U
}

object LoadStoreObj extends App {
  (new ChiselStage).emitVerilog(new LoadStoreWrapper(64, 128*1024, "test.hex"))
}
