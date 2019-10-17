import org.scalatest._
import chisel3.tester._
import chisel3._

import TestValues._

class SimpleMultiplierUnitTester extends FlatSpec with ChiselScalatestTester with Matchers {
  behavior of "SimpleMultiplier"

  val tests = for {
    //x <- testValuesShort
    //y <- testValuesShort
    x <- testValuesRealShort
    y <- testValuesRealShort
  } yield (x, y)

  def mult(a: BigInt, b: BigInt): BigInt = (a * b) & BigInt("ffffffffffffffff", 16)

  def multHigh(a: BigInt, b: BigInt): BigInt = ((a * b) >> 64) & BigInt("ffffffffffffffff", 16)

  def multHighSigned(a: BigInt, b: BigInt): BigInt = {
    val aSigned = if (a.testBit(63)) (BigInt("ffffffffffffffff", 16) << 64) + a else a
    val bSigned = if (b.testBit(63)) (BigInt("ffffffffffffffff", 16) << 64) + b else b
    multHigh(aSigned, bSigned)
  }

  it should "pass a unit test" in {
    test(new SimpleMultiplier(64)) { m =>
      for ((x, y) <- tests) {
        while (m.io.in.ready.peek().litToBoolean == false) {
          m.clock.step(1)
        }

        m.io.in.bits.a.poke(x.U)
        m.io.in.bits.b.poke(y.U)
        m.io.in.valid.poke(true.B)
        m.clock.step(1)
        m.io.in.valid.poke(false.B)

        while (m.io.out.valid.peek().litToBoolean == false) {
          m.clock.step(1)
        }

        m.io.out.bits.expect(mult(x, y).U)
      }

      m.io.in.bits.high.poke(true.B)

      for ((x, y) <- tests) {
        while (m.io.in.ready.peek().litToBoolean == false) {
          m.clock.step(1)
        }

        m.io.in.bits.a.poke(x.U)
        m.io.in.bits.b.poke(y.U)
        m.io.in.valid.poke(true.B)
        m.clock.step(1)
        m.io.in.valid.poke(false.B)

        while (m.io.out.valid.peek().litToBoolean == false) {
          m.clock.step(1)
        }

        m.io.out.bits.expect(multHigh(x, y).U)
      }

      m.io.in.bits.signed.poke(true.B)

      for ((x, y) <- tests) {
        while (m.io.in.ready.peek().litToBoolean == false) {
          m.clock.step(1)
        }

        m.io.in.bits.a.poke(x.U)
        m.io.in.bits.b.poke(y.U)
        m.io.in.valid.poke(true.B)
        m.clock.step(1)
        m.io.in.valid.poke(false.B)

        while (m.io.out.valid.peek().litToBoolean == false) {
          m.clock.step(1)
        }

        m.io.out.bits.expect(multHighSigned(x, y).U)
      }

    }
  }
}
