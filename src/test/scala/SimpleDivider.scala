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

  def divide(a: BigInt, b: BigInt): BigInt = (a / b) & BigInt("ffffffffffffffff", 16)

  def divideExtended(a: BigInt, b: BigInt): BigInt = (((a << 64) / b) >> 64) & BigInt("ffffffffffffffff", 16)

  it should "pass a unit test" in {
    test(new SimpleDivider(64)) { m =>
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

  }
}
