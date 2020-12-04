import chisel3._
import chisel3.util.{log2Ceil, MuxCase, Reverse}
import chisel3.stage.ChiselStage

/** Module for counting leading zeroes in a [[UInt]]
  * @param n the width of the [[UInt]]
  */
class CountZeroes(bits: Int) extends Module {
  val io = IO(new Bundle {
    val a          = Input(UInt(bits.W))
    val countRight = Input(Bool())
    val is32bit    = Input(Bool())
    val out        = Output(UInt(log2Ceil(bits+1).W))
  })

  /** Count leading zeroes in a pair of bits:
    * 00   -> 2
    * 01   -> 1
    * else -> 0
    */
  private def clzTwoBit(a: UInt): UInt = MuxCase(0.U, Seq((a === 0.U) -> 2.U, (a === 1.U) -> 1.U))

  /** Reduction step. We take two numbers that contain the number of
    * leading zero bits in each limb and reduce that to a single number
    * representing the total number of leading zero bits. The result
    * is one bit larger.
    *
    * eg for n = 4:
    * 1aaa 1zzz -> 10000 (No '1' bits in either)
    * 01ab zzzz -> 001ab (A '1' in the upper region)
    * 1aaa zyxw -> 01zyx (A '1' in the lower region)
    */
  private def clzReduce(hi: UInt, lo: UInt, n: Int): UInt = {
    val x = Wire(UInt((n+1).W))

    when (hi(n-1) === 0.U) { x := (hi(n-1) & lo(n-1)) ## 0.U(1.W) ## hi(n-2, 0) }
      .otherwise           { x := (hi(n-1) & lo(n-1)) ## ~lo(n-1) ## lo(n-2, 0) }

    x
  }

  /** Recursively calculate the number of leading zeroes */
  private def sumZeroes(n: Int, offset: Int): UInt = n match {
    case 1 => clzTwoBit(a(offset*2+1, offset*2))
    case _ => clzReduce(sumZeroes(n-1, offset*2+1), sumZeroes(n-1, offset*2), n)
  }

  val reversed = Mux(io.countRight, Reverse(io.a), io.a)

  val a = WireDefault(UInt((bits).W), reversed)
  when (io.is32bit) {
    when (!io.countRight) {
      a := reversed(31, 0) ## reversed(31, 0) | ((1L << 31).U)
    }.otherwise {
      a := reversed | ((1L << 31).U)
    }
  }

  io.out := sumZeroes(log2Ceil(bits), 0)
}

object CountZeroesObj extends App {
  (new ChiselStage).emitVerilog(new CountZeroes(64))
}

/** Utilities for counting zeroes */
object CountLeadingZeroes {

  /** Return a [[UInt]] containing the number of leading zeroes in some [[Data]]
    * @param a a hardware type
    * @tparam A some data
    */
  def apply[A <: Data](a: A): UInt = {
    val count = Module(new CountZeroes(a.getWidth))
    count.io.a := a.asUInt
    count.io.countRight := false.B
    count.io.is32bit := false.B
    count.io.out
  }
}

object CountTrailingZeroes {

  /** Return a [[UInt]] containing the number of leading zeroes in some [[Data]]
    * @param a a hardware type
    * @tparam A some data
    */
  def apply[A <: Data](a: A): UInt = {
    val count = Module(new CountZeroes(a.getWidth))
    count.io.a := a.asUInt
    count.io.countRight := true.B
    count.io.is32bit := false.B
    count.io.out
  }
}
