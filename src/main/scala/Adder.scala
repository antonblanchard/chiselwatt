import chisel3._

class Adder(n: Int) extends Module {
  val io = IO(new Bundle {
    val a         = Input(UInt(n.W))
    val b         = Input(UInt(n.W))
    val carryIn   = Input(UInt(1.W))
    val invertIn  = Input(UInt(1.W))
    val is32bit   = Input(UInt(1.W))
    val signed    = Input(UInt(1.W))
    val out       = Output(UInt(n.W))
    val carryOut  = Output(UInt(1.W))
    val ltOut     = Output(UInt(1.W))
  })

  val a = Mux(io.invertIn.asBool, ~io.a, io.a)
  val x = a +& io.b + io.carryIn

  val aSign = if (n == 64) Mux(io.is32bit.asBool, io.a(31), io.a(63)) else io.a(31)
  val bSign = if (n == 64) Mux(io.is32bit.asBool, io.b(31), io.b(63)) else io.b(31)
  val xSign = if (n == 64) Mux(io.is32bit.asBool, x(31), x(63)) else x(31)

  /*
   * Our adder does b - a, which is reverse to what compare wants, so
   * we invert the sign of x. We calculate the rest of the compare bits
   * in a subsequent cycle.
   */
  io.ltOut := Mux(aSign === bSign, ~xSign,
    Mux(io.signed.asBool, aSign, bSign))

  io.out := x((n-1), 0)
  io.carryOut := x(n)
}

object AdderObj extends App {
  chisel3.Driver.execute(Array[String](), () => new Adder(64))
}
