import chisel3._
import chisel3.util.{Valid, Decoupled, log2Ceil}

import Helpers._

class DividerInput(val bits: Int) extends Bundle {
  val dividend = UInt(bits.W)
  val divisor  = UInt(bits.W)
  val is32bit  = Bool()
  val signed   = Bool()
  val extended = Bool()
  val modulus  = Bool()
}

class SimpleDivider(val bits: Int) extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(Decoupled(new DividerInput(bits)))
    val out = Output(Valid(UInt(bits.W)))
  })

  val dividend = Reg(UInt((bits*2+1).W))
  val divisor = Reg(UInt(bits.W))
  val quotient = Reg(UInt(bits.W))
  val is32bit = Reg(Bool())
  //val signed = Reg(Bool())
  val modulus = Reg(Bool())
  val count = Reg(UInt(log2Ceil(bits+1).W))
  val busy = RegInit(false.B)

  io.in.ready := !busy

  when (io.in.valid && !busy) {
    when (io.in.bits.is32bit) {
      when (io.in.bits.extended) {
        dividend := io.in.bits.dividend(31, 0) ## 0.U(32.W)
      } .otherwise {
        dividend := io.in.bits.dividend(31, 0)
      }
      divisor := io.in.bits.divisor(31, 0)
    } .otherwise {
      when (io.in.bits.extended) {
        dividend := io.in.bits.dividend ## 0.U(bits.W)
      } .otherwise {
        dividend := 0.U((bits+1).W) ## io.in.bits.dividend
      }
      divisor := io.in.bits.divisor
    }

    is32bit := io.in.bits.is32bit
    quotient := 0.U
    count := 0.U
    modulus := io.in.bits.modulus
    busy := true.B
  }

  when (busy) {
    when ((dividend(bits*2) === 1.U) || (dividend(bits*2-1, bits) >= divisor)) {
        dividend := (dividend(bits*2-1, bits) - divisor) ## dividend(bits-1, 0) ## 0.U(1.W)
        quotient := quotient(bits-2, 0) ## 1.U(1.W)
      } .otherwise {
        dividend := dividend((bits*2-1), 0) ## 0.U(1.W)
        quotient := quotient(bits-2, 0) ## 0.U(1.W)
    }
    count := count + 1.U
  }

  // Did we shift out a 1? This also handles divide by zero
  val overflow = Reg(Bool())
  overflow := quotient(bits-1)
  when (is32bit) {
    overflow := quotient(63, 31).orR
  }

  val result = WireDefault(quotient)
  when (overflow) {
    result := 0.U
  } .elsewhen (is32bit && !modulus) {
    result := 0.U(32.W) ## quotient(31, 0)
  }

  io.out.bits := RegNext(result)
  io.out.valid := RegNext((count === (bits+1).U) && busy)
  when (io.out.valid) {
    busy := false.B
  }
}

object SimpleDividerObj extends App {
  chisel3.Driver.execute(Array[String](), () => new SimpleDivider(64))
}
