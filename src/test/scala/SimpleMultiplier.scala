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

  def mult32(a: BigInt, b: BigInt): BigInt = {
    val a32 = a & BigInt("ffffffff", 16)
    val b32 = b & BigInt("ffffffff", 16)

    (a32 * b32)
  }

  def multHigh32(a: BigInt, b: BigInt): BigInt = {
    val a32 = a & BigInt("ffffffff", 16)
    val b32 = b & BigInt("ffffffff", 16)

    val m = ((a32 * b32) >> 32) & BigInt("ffffffff", 16)
    (m << 32) | m
  }

  def multHighSigned32(a: BigInt, b: BigInt): BigInt = {
    val a32 = a & BigInt("ffffffff", 16)
    val b32 = b & BigInt("ffffffff", 16)
    val aSigned = if (a32.testBit(31)) (BigInt("ffffffff", 16) << 32) + a32 else a32
    val bSigned = if (b32.testBit(31)) (BigInt("ffffffff", 16) << 32) + b32 else b32

    val m = ((aSigned * bSigned) >> 32) & BigInt("ffffffff", 16)
    (m << 32) | m
  }

  def runOneTest(m: SimpleMultiplier, mult: (BigInt, BigInt) => BigInt) = {
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
  }

  it should "pass a unit test" in {
    test(new SimpleMultiplier(64)) { m =>

      runOneTest(m, mult)

      m.io.in.bits.high.poke(true.B)
      runOneTest(m, multHigh)

      m.io.in.bits.signed.poke(true.B)
      runOneTest(m, multHighSigned)

      m.io.in.bits.signed.poke(false.B)
      m.io.in.bits.high.poke(false.B)

      m.io.in.bits.is32bit.poke(true.B)
      runOneTest(m, mult32)

      m.io.in.bits.high.poke(true.B)
      runOneTest(m, multHigh32)

      m.io.in.bits.signed.poke(true.B)
      runOneTest(m, multHighSigned32)
    }
  }
}
