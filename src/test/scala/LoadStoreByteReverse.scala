import chisel3._
import chiseltest._
import Control._
import org.scalatest.flatspec.AnyFlatSpec

class LoadStoreByteReverseTester extends AnyFlatSpec with ChiselScalatestTester  {
  val x = BigInt("0123456789ABCDEF", 16)
  val bits = 64

  behavior of "LoadStoreByteReverse"
  it should "pass a unit test" in {
    test(new LoadStoreByteReverse(bits)) { br =>
      br.io.in.poke(x.U)

      br.io.length.poke(LEN_2B)
      br.io.out.expect("h000000000000EFCD".U)

      br.io.length.poke(LEN_4B)
      br.io.out.expect("h00000000EFCDAB89".U)

      br.io.length.poke(LEN_8B)
      br.io.out.expect("hEFCDAB8967452301".U)
    }
  }
}
