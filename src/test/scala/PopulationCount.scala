import org.scalatest._
import chisel3.tester._
import chisel3._

import Control._
import TestValues._

class PopulationCountUnitTester extends FlatSpec with ChiselScalatestTester with Matchers {
  behavior of "PopulationCount"

  private def popcntb(x: BigInt): BigInt = {
    var ret : BigInt = 0

    for (i <- 0 until 8) {
      ret = ret + (BigInt(((x >> (i*8)) & 0xff).bitCount) << (i*8))
    }

    ret
  }

  private def popcntw(x: BigInt): BigInt = {
    var ret : BigInt = 0

    for (i <- 0 until 2) {
      ret = ret + (BigInt(((x >> (i*32)) & 0xffffffffL).bitCount) << (i*32))
    }

    ret
  }

  private def popcntd(x: BigInt): BigInt = x.bitCount

  it should "pass a unit test" in {
    test(new PopulationCount(64)) { p =>
      p.io.length.poke(LEN_1B)
      for (x <- testValues) {
        p.io.a.poke(x.U)
        p.io.out.expect(popcntb(x).U)
      }

      p.io.length.poke(LEN_4B)
      for (x <- testValues) {
        p.io.a.poke(x.U)
        p.io.out.expect(popcntw(x).U)
      }

      p.io.length.poke(LEN_8B)
      for (x <- testValues) {
        p.io.a.poke(x.U)
        p.io.out.expect(popcntd(x).U)
      }
    }
  }
}
