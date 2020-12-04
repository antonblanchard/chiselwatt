import chisel3._
import chisel3.util._
import chisel3.stage.ChiselStage

/*
 * A simple TTL serial module. Idle is high. Start bits are low, stop bits
 * are high and we send each byte out LSB first. We only implement 8n1,
 * ie 1 start bit, no parity and 1 stop bit. There is no flow control, so
 * we might overflow the RX fifo if the rxQueue consumer can't keep up.
 *
 * We oversample RX. clockDivisor is set as follows:
 *
 * clockDivisor = (clock_freq / (baudrate * rxOverclock)) - 1
 *
 * When programming the divider, software currently assumes we overclock 16x.
 *
 * Also note that if the input clock frequency is low enough then the errors
 * introduced by oversampling might be significant. Eg at 10MHz, 115200 baud,
 * 16x oversampling we have almost 8% error and the UART fails to work.
 */

class Uart(val fifoLength: Int, val rxOverclock: Int) extends Module {
  val io = IO(new Bundle {
    val rx           = Input(UInt(1.W))
    val tx           = Output(UInt(1.W))
    val rxQueue      = Decoupled(UInt(8.W))
    val txQueue      = Flipped(Decoupled(UInt(8.W)))
    val rxEmpty      = Output(Bool())
    val txEmpty      = Output(Bool())
    val rxFull       = Output(Bool())
    val txFull       = Output(Bool())
    val clockDivisor = Input(UInt(8.W))
  })

  require(isPow2(rxOverclock))

  val sampleClk = RegInit(0.U(1.W))
  val sampleClkCounter = RegInit(0.U(8.W))

  val txQueue = Module(new Queue(UInt(8.W), fifoLength))
  val rxQueue = Module(new Queue(UInt(8.W), fifoLength))

  io.rxEmpty := (rxQueue.io.count === 0.U)
  io.txEmpty := (txQueue.io.count === 0.U)
  io.rxFull  := (rxQueue.io.count === fifoLength.U)
  io.txFull  := (txQueue.io.count === fifoLength.U)

  val uartEnabled = io.clockDivisor.orR

  when (uartEnabled) {
    when (sampleClkCounter === io.clockDivisor) {
      sampleClk := 1.U
      sampleClkCounter := 0.U
    } .otherwise {
      sampleClk := 0.U
      sampleClkCounter := sampleClkCounter + 1.U
    }
  } .otherwise {
    sampleClk := 0.U
    sampleClkCounter := 0.U
  }

  /*
   * Our TX clock needs to match our baud rate. This means we need to
   * further divide sampleClk by rxOverclock.
   */
  val (txCounterValue, txCounterWrap) = Counter(sampleClk === 1.U, rxOverclock)
  val txClk = txCounterWrap

  /* TX */
  val sTxIdle :: sTxTx :: sTxStop :: Nil = Enum(3)
  val txState = RegInit(sTxIdle)
  val txByte = Reg(UInt(8.W))
  val txByteBit = Reg(UInt(3.W))
  /* RS232 idle state is high */
  val tx = RegInit(1.U(1.W))

  txQueue.io.deq.ready := false.B

  /* We run the tx state machine off the txClk */
  when (txClk === 1.U) {
    switch (txState) {
      is (sTxIdle) {
        /* Does the FIFO contain data? */
        when (txQueue.io.deq.valid) {
          txQueue.io.deq.ready := true.B
          txByte := txQueue.io.deq.bits
          txByteBit := 0.U
          txState := sTxTx
          /* Send start bit */
          tx := 0.U
        } .otherwise {
          tx := 1.U
        }
      }

      is (sTxTx) {
        when (txByteBit === 7.U) {
          txState := sTxStop
        }
        tx := txByte(txByteBit)
        txByteBit := txByteBit + 1.U
      }

      is (sTxStop) {
        txState := sTxIdle
        /* Send stop bit */
        tx := 1.U
      }
    }
  }

  io.tx := tx

  /* RX */
  val sRxIdle :: sRxStart :: sRxRx :: sRxStop :: sRxError1 :: sRxError2 :: Nil = Enum(6)
  val rxState = RegInit(sRxIdle)
  val rxCounter = RegInit(0.U(log2Ceil(rxOverclock).W))
  val rxByte = RegInit(VecInit(Seq.fill(8)(0.U(1.W))))
  val rxByteBit = RegInit(0.U(3.W))

  rxQueue.io.enq.bits := 0.U
  rxQueue.io.enq.valid := false.B

  /* Synchronize the input to avoid any metastability issues */
  val rx = RegNext(RegNext(RegNext(io.rx)))

  when (sampleClk === 1.U) {
    switch (rxState) {
      is (sRxIdle) {
        when (rx === 0.U) {
          rxState := sRxStart
          rxCounter := 1.U
          rxByte := Seq.fill(8){0.U}
          rxByteBit := 0.U
        }
      }

      is (sRxStart) {
        rxCounter := rxCounter + 1.U
        /* We should be at the middle of the start bit */
        when (rxCounter === (rxOverclock/2).U) {
          rxCounter := 1.U
          rxState := sRxRx
        }
      }

      is (sRxRx) {
        rxCounter := rxCounter + 1.U
        when (rxCounter === 0.U) {
          when (rxByteBit === 7.U) {
            rxState := sRxStop
          }
          rxByte(rxByteBit) := rx
          rxByteBit := rxByteBit + 1.U
        }
      }

      is (sRxStop) {
        rxCounter := rxCounter + 1.U
        when (rxCounter === 0.U) {
          /* Ensure the stop bit is high */
          when (rx === 1.U) {
            /* We might overflow the queue if we can't keep up */
            rxQueue.io.enq.bits := rxByte.reverse.reduce(_ ## _)
            rxQueue.io.enq.valid := true.B
            rxState := sRxIdle
          } .otherwise {
            /* Something went wrong */
            rxState := sRxError1
          }
        }
      }

      /*
       * We might not have synchronised on a start bit. Delay 2 bits on
       * each error so we eventually line up with the start bit.
       */
      is (sRxError1) {
        /* Delay one bit */
        rxCounter := rxCounter + 1.U
        when (rxCounter === 0.U) {
          rxState := sRxError2
        }
      }

      is (sRxError2) {
        /* Delay another bit */
        rxCounter := rxCounter + 1.U
        when (rxCounter === 0.U) {
          rxState := sRxIdle
        }
      }
    }
  }

 rxQueue.io.deq <> io.rxQueue
 txQueue.io.enq <> io.txQueue
}

object UartObj extends App {
  (new ChiselStage).emitVerilog(new Uart(64, 16))
}
