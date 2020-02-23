import org.scalatest._
import chisel3.tester._
import chisel3._

import TestValues._

class SimpleDividerUnitTester extends FlatSpec with ChiselScalatestTester with Matchers {
  behavior of "SimpleDivider"

  val tests = for {
    //x <- testValuesShort
    //y <- testValuesShort
    x <- testValuesRealShort
    y <- testValuesRealShort
  } yield (x, y)

  def twosComplement(a: BigInt, bits: Int) = (~a+1) & ((BigInt("1") << bits) - 1)
  def addSign(a: BigInt, bits: Int) = if (a.testBit(bits-1)) -twosComplement(a, bits) else a
  def removeSign(a: BigInt, bits: Int) = if (a < 0) twosComplement(-a, bits) else a

  def divide(a: BigInt, b: BigInt): BigInt = if (b == 0) 0 else (a / b)

  def divideSigned(a: BigInt, b: BigInt): BigInt = {
    val d = removeSign(divide(addSign(a, 64), addSign(b, 64)), 65)

    // Check for overflow
    if (d.testBit(64) != d.testBit(64-1)) 0 else (d & ~(BigInt("1") << 64))
  }

  def divideExtended(a: BigInt, b: BigInt): BigInt = {
    val d = divide((a << 64), b)
    if ((d >> 64) == 0) d else 0
  }

  def runOneTest(m: SimpleDivider, divide: (BigInt, BigInt) => BigInt) = {
    for ((x, y) <- tests) {

      while (m.io.in.ready.peek().litToBoolean == false) {
        m.clock.step(1)
      }

      m.io.in.bits.dividend.poke(x.U)
      m.io.in.bits.divisor.poke(y.U)
      m.io.in.valid.poke(true.B)
      m.clock.step(1)
      m.io.in.valid.poke(false.B)

      while (m.io.out.valid.peek().litToBoolean == false) {
        m.clock.step(1)
      }

      m.io.out.bits.expect(divide(x, y).U)
    }
  }

  it should "pass a unit test" in {
    test(new SimpleDivider(64)) { m =>
      runOneTest(m, divide)

      m.io.in.bits.extended.poke(true.B)
      runOneTest(m, divideExtended)

      m.io.in.bits.extended.poke(false.B)
      m.io.in.bits.signed.poke(true.B)
      runOneTest(m, divideSigned)
    }

  }
}
