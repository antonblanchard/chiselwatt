import org.scalatest._
import chisel3.tester._
import chisel3._

import chiseltest.experimental.TestOptionBuilder._
import chiseltest.internal.{VerilatorBackendAnnotation, WriteVcdAnnotation}

import treadle.executable.ClockInfo
import treadle.{ClockInfoAnnotation}

import Control._

class LoadStoreUnitTester extends FlatSpec with ChiselScalatestTester with Matchers {
  val bits = 64
  val words = 1024
  val filename = "LoadStoreInsns.hex"

  private def doOneRead(m: LoadStoreWrapper, a: UInt, b: UInt, length: UInt, signed: UInt, byteReverse: UInt, expected: UInt) = {
      m.io.in.bits.a.poke(a)
      m.io.in.bits.b.poke(b)
      m.io.in.bits.internalOp.poke(LDST_LOAD)
      m.io.in.bits.length.poke(length)
      m.io.in.bits.signed.poke(signed)
      m.io.in.bits.byteReverse.poke(byteReverse)
      m.io.in.bits.update.poke(0.U)
      m.io.in.bits.reservation.poke(0.U)
      m.io.in.valid.poke(true.B)
      m.io.out.valid.expect(false.B)
      m.clock.step()

      m.io.in.valid.poke(false.B)
      m.io.out.valid.expect(false.B)
      m.clock.step()

      m.io.out.valid.expect(true.B)
      m.io.out.bits.expect(expected)
      m.clock.step()
  }

  private def doOneWrite(m: LoadStoreWrapper, a: UInt, b: UInt, length: UInt, signed: UInt, byteReverse: UInt, data: UInt) = {
      m.io.in.bits.a.poke(a)
      m.io.in.bits.b.poke(b)
      m.io.in.bits.data.poke(data)
      m.io.in.bits.internalOp.poke(LDST_STORE)
      m.io.in.bits.length.poke(length)
      m.io.in.bits.signed.poke(signed)
      m.io.in.bits.byteReverse.poke(byteReverse)
      m.io.in.bits.update.poke(0.U)
      m.io.in.bits.reservation.poke(0.U)
      m.io.in.valid.poke(true.B)
      m.io.out.valid.expect(false.B)
      m.clock.step()

      m.io.in.valid.poke(false.B)
      m.io.out.valid.expect(true.B)
      m.clock.step()

      doOneRead(m, a, b, length, signed, byteReverse, data)
  }

  reflect.io.File(filename).writeAll("0001020304050607\r\n08090A0B0C0D0E0F\r\n0F0E0D0C0B0A0908\r\n8080808080808080\r\n")

  behavior of "LoadStore"
  it should "pass a unit test" in {
    test(new LoadStoreWrapper(bits, words, filename)).withAnnotations(Seq(VerilatorBackendAnnotation, WriteVcdAnnotation, ClockInfoAnnotation(Seq(ClockInfo(period = 2))))) { m =>

      doOneRead(m, 0.U, 0.U, LEN_1B, 0.U, 0.U, "h07".U)
      doOneRead(m, 0.U, 0.U, LEN_2B, 0.U, 0.U, "h0607".U)
      doOneRead(m, 0.U, 0.U, LEN_4B, 0.U, 0.U, "h04050607".U)
      doOneRead(m, 0.U, 0.U, LEN_8B, 0.U, 0.U, "h0001020304050607".U)

      /* Test offsets within 64 bits */
      doOneRead(m, 1.U, 0.U, LEN_1B, 0.U, 0.U, "h06".U)
      doOneRead(m, 1.U, 1.U, LEN_2B, 0.U, 0.U, "h0405".U)
      doOneRead(m, 2.U, 2.U, LEN_4B, 0.U, 0.U, "h00010203".U)

      /* Test byte reverse */
      doOneRead(m, 0.U, 0.U, LEN_2B, 0.U, 1.U, "h0706".U)
      doOneRead(m, 0.U, 0.U, LEN_4B, 0.U, 1.U, "h07060504".U)
      doOneRead(m, 0.U, 0.U, LEN_8B, 0.U, 1.U, "h0706050403020100".U)

      /* Test sign extension */
      doOneRead(m, 24.U, 0.U, LEN_1B, 1.U, 0.U, "hFFFFFFFFFFFFFF80".U)
      doOneRead(m, 24.U, 0.U, LEN_2B, 1.U, 0.U, "hFFFFFFFFFFFF8080".U)
      doOneRead(m, 24.U, 0.U, LEN_4B, 1.U, 0.U, "hFFFFFFFF80808080".U)

      /* Test basic writes */
      doOneWrite(m, 0.U, 0.U, LEN_1B, 0.U, 0.U, "h5A".U)
      doOneWrite(m, 0.U, 0.U, LEN_2B, 0.U, 0.U, "h9875".U)
      doOneWrite(m, 0.U, 0.U, LEN_4B, 0.U, 0.U, "h03562598".U)
      doOneWrite(m, 0.U, 0.U, LEN_8B, 0.U, 0.U, "hBADBADBADC0FFEE0".U)

      /* Test offsets within 64 bits */
      doOneWrite(m, 0.U, 0.U, LEN_8B, 0.U, 0.U, "h0706050403020100".U)
      doOneWrite(m, 1.U, 0.U, LEN_1B, 0.U, 0.U, "h77".U)
      doOneRead(m, 0.U, 0.U, LEN_8B, 0.U, 0.U, "h0706050403027700".U)
      doOneWrite(m, 2.U, 0.U, LEN_2B, 0.U, 0.U, "h2941".U)
      doOneRead(m, 0.U, 0.U, LEN_8B, 0.U, 0.U, "h0706050429417700".U)
      doOneWrite(m, 4.U, 0.U, LEN_4B, 0.U, 0.U, "h71492952".U)
      doOneRead(m, 0.U, 0.U, LEN_8B, 0.U, 0.U, "h7149295229417700".U)

      /* Test byte reverse */
      doOneWrite(m, 0.U, 0.U, LEN_8B, 0.U, 0.U, "h0706050403020100".U)
      doOneWrite(m, 0.U, 0.U, LEN_2B, 0.U, 1.U, "h0706".U)
      doOneWrite(m, 0.U, 0.U, LEN_4B, 0.U, 1.U, "h07060504".U)
      doOneWrite(m, 0.U, 0.U, LEN_8B, 0.U, 1.U, "h0706050403020100".U)

      /* Test sign extend */
      doOneWrite(m, 8.U, 0.U, LEN_1B, 1.U, 0.U, "hFFFFFFFFFFFFFF81".U)
      doOneWrite(m, 8.U, 0.U, LEN_2B, 1.U, 0.U, "hFFFFFFFFFFFF8181".U)
      doOneWrite(m, 8.U, 0.U, LEN_4B, 1.U, 0.U, "hFFFFFFFF81818181".U)
    }
  }
}
