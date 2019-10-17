import chisel3._
import chisel3.util.MuxLookup

import Control._

object Helpers {
  implicit class BitsHelpers(a: Bits) {

    def nibbles(): Vec[UInt] = {
      require(a.isWidthKnown, "Conversion to 'nibbles' requires width to be known!")
      a.asTypeOf(Vec(a.getWidth/4, UInt(4.W)))
    }
    def nibbles(n: Int): UInt = a((n + 1) * 4 - 1, n * 4)

    def bytes(): Vec[UInt] = {
      require(a.isWidthKnown, "Conversion to 'bytes' requires width to be known!")
      a.asTypeOf(Vec(a.getWidth/8, UInt(8.W)))
    }
    def bytes(n: Int): UInt = a((n + 1) * 8 - 1, n * 8)

    def halfwords(): Vec[UInt] = {
      require(a.isWidthKnown, "Conversion to 'halfwords' requires width to be known!")
      a.asTypeOf(Vec(a.getWidth/16, UInt(16.W)))
    }
    def halfwords(n: Int): UInt = a((n + 1) * 16 - 1, n * 16)

    def words(): Vec[UInt] = {
      require(a.isWidthKnown, "Conversion to 'words' requires width to be known!")
      a.asTypeOf(Vec(a.getWidth/32, UInt(32.W)))
    }
    def words(n: Int): UInt = a((n + 1) * 32 - 1, n * 32)
  }

  implicit class signExtend(a: Bits) {
    def signExtend(from: Int, to: Int): UInt = a(from-1, 0).asSInt.pad(to).asUInt

    def signExtend(from: UInt): UInt = {
      val lookupTable = Seq(LEN_1B, LEN_2B, LEN_4B).zip(Seq(8, 16, 32).map(frm => a(frm-1, 0).asSInt.pad(a.getWidth).asUInt))

      MuxLookup(from, lookupTable.head._2, lookupTable)
    }
  }

  implicit class zeroExtend(a: Bits) {
    def zeroExtend(from: Int, to: Int): UInt = a(from-1, 0).pad(to)

    def zeroExtend(from: UInt): UInt = {
      val lookupTable = Seq(LEN_1B, LEN_2B, LEN_4B, LEN_8B).zip(Seq(8, 16, 32, 64).map(frm => a(frm-1, 0).pad(a.getWidth)))

      MuxLookup(from, lookupTable.head._2, lookupTable)
    }
  }
}
