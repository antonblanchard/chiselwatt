import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class MemoryBlackBoxUnitTester extends AnyFlatSpec with ChiselScalatestTester {
  val bits = 64
  val words = 1024
  val filename = "MemoryBlackBoxInsns.hex"

  reflect.io.File(filename).writeAll("0001020304050607\r\n08090A0B0C0D0E0F\r\n0F0E0D0C0B0A0908\r\n0706050403020100\r\n")

  behavior of "MemoryBlackBox"
  it should "pass a unit test" in {
    test(new MemoryBlackBoxWrapper(bits, words, filename)).withAnnotations(Seq(VerilatorBackendAnnotation, WriteVcdAnnotation)) { m =>

      m.io.fetchPort.addr.poke(0.U)

      m.clock.step()
      m.io.fetchPort.readData.expect("h0001020304050607".U)

      m.io.fetchPort.addr.poke(1.U)
      m.clock.step()
      m.io.fetchPort.readData.expect("h08090A0B0C0D0E0F".U)

      m.io.loadStorePort.addr.poke(0.U)
      m.io.loadStorePort.writeData.poke("hAAAAAAAAAAAAAAAA".U)
      m.io.loadStorePort.writeEnable.poke(true.B)
      m.io.loadStorePort.writeMask.poke("hFF".U)
      m.clock.step()
      m.io.loadStorePort.writeEnable.poke(false.B)
      m.io.loadStorePort.writeMask.poke(0.U)

      m.io.fetchPort.addr.poke(0.U)
      m.clock.step()
      m.io.fetchPort.readData.expect("hAAAAAAAAAAAAAAAA".U)
    }
  }
}
