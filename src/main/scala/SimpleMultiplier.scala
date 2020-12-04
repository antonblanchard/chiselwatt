import chisel3._
import chisel3.util.{Valid, Decoupled, log2Ceil}
import chisel3.stage.ChiselStage

import Helpers._

class MultiplierInput(val bits: Int) extends Bundle {
  val a = UInt(bits.W)
  val b = UInt(bits.W)
  val is32bit = Bool()
  val signed = Bool()
  val high = Bool()
}

class SimpleMultiplier(val bits: Int) extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(Decoupled(new MultiplierInput(bits)))
    val out = Output(Valid(UInt(bits.W)))
  })

  val a = Reg(UInt(bits.W))
  val b = Reg(UInt((2*bits).W))
  val is32bit = Reg(Bool())
  val high = Reg(Bool())
  val res = Reg(UInt((2*bits).W))
  val busy = RegInit(false.B)
  val count = Reg(UInt(log2Ceil(bits+1).W))

  io.in.ready := !busy

  when (io.in.valid && !busy) {
    val aSignExtend = WireDefault(io.in.bits.a)
    val bSignExtend = WireDefault(io.in.bits.b)

    /* Sign or zero extend 32 bit values */
    when (io.in.bits.is32bit) {
      when (io.in.bits.signed) {
        aSignExtend := io.in.bits.a.signExtend(32, bits)
        bSignExtend := io.in.bits.b.signExtend(32, bits)
      } .otherwise {
        aSignExtend := io.in.bits.a.zeroExtend(32, bits)
        bSignExtend := io.in.bits.b.zeroExtend(32, bits)
      }
    }

    when (io.in.bits.signed) {
      /*
       * We always want a positive value in a, so take the two's complement
       * of both args if a is negative
       */
      when (aSignExtend(bits-1)) {
        a := -aSignExtend
        b := -(bSignExtend.signExtend(bits, 2*bits))
      } .otherwise {
        a := aSignExtend
        b := bSignExtend.signExtend(bits, 2*bits)
      }
    } .otherwise {
      a := aSignExtend
      b := bSignExtend
    }

    is32bit := io.in.bits.is32bit
    high := io.in.bits.high
    res := 0.U
    busy := true.B
    count := 0.U
  }

  when (busy) {
    when (a(0) === 1.U) {
      res := res + b
    }
    b := b << 1
    a := a >> 1
    count := count + 1.U
  }

  val result = WireDefault(res(63, 0))
  when (high) {
    when (is32bit) {
      result := res(63, 32) ## res(63, 32)
    } .otherwise {
      result := res(127, 64)
    }
  }

  io.out.bits := RegNext(result)
  io.out.valid := RegNext((count === (bits+1).U) && busy)
  when (io.out.valid) {
    busy := false.B
  }
}

object SimpleMultiplierObj extends App {
  (new ChiselStage).emitVerilog(new SimpleMultiplier(64))
}
