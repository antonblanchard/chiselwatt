import org.scalatest._
import chisel3.tester._
import chisel3._

class AlignStoreTester extends FlatSpec with ChiselScalatestTester with Matchers {
  behavior of "AlignStore"
  it should "pass a unit test" in {
    test(new LoadStoreAlign(64)) { p =>
      val x = BigInt(0x0102030405060708L)
      p.io.in.poke(x.U)

      for (offset <- 0 until 7) {
        val y = (x << (offset*8)) & BigInt("FFFFFFFFFFFFFFFF", 16)
        p.io.offset.poke(offset.U)
        p.io.out.expect(y.U)
      }
    }
  }
}
