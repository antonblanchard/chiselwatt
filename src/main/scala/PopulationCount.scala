import chisel3._
import chisel3.util.{PopCount, MuxLookup}
import chisel3.stage.ChiselStage

import Control._
import Helpers._

class PopulationCount(bits: Int) extends Module {
  val io = IO(new Bundle {
    val a      = Input(UInt(bits.W))
    val length = Input(UInt(2.W))
    val out    = Output(UInt(bits.W))
  })

  // Move helper to somewhere common
  def lengthToBits(n: UInt) = {
    val lengths = Seq(8, 16, 32, 64)
    val lengthNames = Seq(LEN_1B, LEN_2B, LEN_4B, LEN_8B)

    lengthNames.zip(lengths).toMap.get(n).getOrElse(0)
  }

  // Note that halfword population count is not in the architecture, so we skip it
  val lengthSeq = if (bits == 64) Seq(LEN_1B, LEN_4B, LEN_8B) else Seq(LEN_1B, LEN_4B)
  val bitSeq = lengthSeq.map(l => lengthToBits(l))

  val b: Seq[UInt] = (((bits/8)-1) to 0 by -1).map{ case i => PopCount(io.a.bytes(i)) }
  val valueSeq = {
    if (bits == 64) {
      val w : Seq[UInt] = {
        val (first, second) = b.splitAt(4)
        // Would a tree reduction be faster?
        Seq(first, second).map{ case a => a.reduce(_ +& _) }
      }
      val d: Seq[UInt] = Seq(w.reduce(_ +& _))
      Seq(b, w, d)
    } else {
      val w: Seq[UInt] = Seq(b.reduce(_ +& _))
      Seq(b, w)
    }
  }

  val popCounts = lengthSeq.zip({
    // Pad values to element size
    valueSeq.zip(bitSeq)
    .map{ case (a, padWidth) => a.map(_.pad(padWidth)).reduce(_ ## _) }
  })

  io.out := MuxLookup(io.length, popCounts.head._1, popCounts)
}

object PopulationCountObj extends App {
  (new ChiselStage).emitVerilog(new PopulationCount(64))
}
