import org.scalatest._
import chisel3.tester._
import chisel3._

class RegisterFileUnitTester extends FlatSpec with ChiselScalatestTester with Matchers {
  behavior of "RegisterFile"

  it should "pass a unit test" in {
    val numRegs = 32

    test(new RegisterFile(numRegs, 64, 2, 2, true)) { r =>
      println("RegisterFileUnitTester begin")

      // Write initial values to registers
      r.io.wr(0).fire().poke(true.B)
      for (x <- (0 until numRegs)) {
        r.io.wr(0).bits.data.poke(x.U)
        r.io.wr(0).bits.addr.poke(x.U)
        r.clock.step()
      }
      r.io.wr(0).fire().poke(false.B)
      r.clock.step()

      // Read them back
      for (x <- (0 until numRegs)) {
        r.io.rd(0).addr.poke(x.U)
        r.io.rd(0).data.expect(x.U)

        r.io.rd(1).addr.poke(x.U)
        r.io.rd(1).data.expect(x.U)
      }

      // Check bypassing works
      r.io.wr(0).fire().poke(true.B)
      r.io.wr(0).bits.data.poke("hBADC0FFEE0DDF00D".U)
      r.io.wr(0).bits.addr.poke(11.U)

      r.io.wr(1).fire().poke(true.B)
      r.io.wr(1).bits.data.poke("hFEE1DEADABADCAFE".U)
      r.io.wr(1).bits.addr.poke(24.U)

      r.io.rd(0).addr.poke(11.U)
      r.io.rd(0).data.expect("hBADC0FFEE0DDF00D".U)
      r.io.rd(1).addr.poke(24.U)
      r.io.rd(1).data.expect("hFEE1DEADABADCAFE".U)
      r.clock.step()

      r.io.wr(0).fire().poke(false.B)
      r.io.wr(1).fire().poke(false.B)
      r.clock.step()

      r.io.rd(0).data.expect("hBADC0FFEE0DDF00D".U)
      r.io.rd(1).data.expect("hFEE1DEADABADCAFE".U)

      println("RegisterFileUnitTester end")
    }
  }
}
