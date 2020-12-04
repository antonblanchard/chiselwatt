import chisel3._
import chisel3.util.{Valid, Decoupled, log2Ceil}
import chisel3.stage.ChiselStage

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
  val negativeResult = Reg(Bool())
  val signed = Reg(Bool())
  val modulus = Reg(Bool())
  val overflow = Reg(UInt(1.W))
  val overflow32 = Reg(UInt(1.W))
  val count = Reg(UInt(log2Ceil(bits+1).W))
  val busy = RegInit(false.B)

  io.in.ready := !busy

  when (io.in.valid && !busy) {
    when (io.in.bits.is32bit) {
      val dividendTemp = WireDefault(io.in.bits.dividend(31, 0))
      when (io.in.bits.signed) {
        dividendTemp := io.in.bits.dividend(31, 0).asSInt.abs().asUInt
        divisor := io.in.bits.divisor(31, 0).asSInt.abs().asUInt
        negativeResult := (io.in.bits.dividend(31) =/= io.in.bits.divisor(31))
      } .otherwise {
        divisor := io.in.bits.divisor(31, 0)
        negativeResult := 0.U
      }

      dividend := Mux(io.in.bits.extended, dividendTemp ## 0.U(32.W), dividendTemp)
    } .otherwise {
      val dividendTemp = WireDefault(io.in.bits.dividend)
      when (io.in.bits.signed) {
        dividendTemp := io.in.bits.dividend.asSInt.abs().asUInt
        divisor := io.in.bits.divisor.asSInt.abs().asUInt
        negativeResult := (io.in.bits.dividend(bits-1) =/= io.in.bits.divisor(bits-1))
      } .otherwise {
        divisor := io.in.bits.divisor
        negativeResult := 0.U
      }

      dividend := Mux(io.in.bits.extended, dividendTemp ## 0.U(bits.W), dividendTemp)
    }

    is32bit := io.in.bits.is32bit
    quotient := 0.U
    signed := io.in.bits.signed
    modulus := io.in.bits.modulus
    overflow := 0.U
    overflow32 := 0.U
    count := 0.U
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

    overflow := quotient(bits-1)
    overflow32 := overflow32 | quotient(32-1)
    count := count + 1.U
  }

  val sResult = Mux(negativeResult, -(0.U(1.W) ## quotient), 0.U(1.W) ## quotient)

  // Catch unsigned and signed overflow, from Paul
  val resultOverflow = WireDefault(false.B)
  when (is32bit) {
    resultOverflow := overflow32.asBool || (signed && (sResult(32) ^ sResult(32-1)))
  } .otherwise {
    resultOverflow := overflow.asBool || (signed && (sResult(bits) ^ sResult(bits-1)))
  }

  val result = WireDefault(sResult(bits-1, 0))
  when (resultOverflow) {
    result := 0.U
  } .elsewhen (is32bit && !modulus) {
    result := sResult(31, 0)
  }

  io.out.bits := RegNext(result)
  io.out.valid := RegNext((count === (bits+1).U) && busy)
  when (io.out.valid) {
    busy := false.B
  }
}

object SimpleDividerObj extends App {
  (new ChiselStage).emitVerilog(new SimpleDivider(64))
}
