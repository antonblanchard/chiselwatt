import chisel3._
import chisel3.util.log2Ceil

import InstructionHelpers._

class Rotator(bits: Int) extends Module {
  val io = IO(new Bundle {
    val ra         = Input(UInt(bits.W))
    val shift      = Input(UInt(log2Ceil(bits+1).W))
    val rs         = Input(UInt(bits.W))
    val is32bit    = Input(Bool())
    val signed     = Input(Bool())
    val rightShift = Input(Bool())
    val clearLeft  = Input(Bool())
    val clearRight = Input(Bool())
    val insn       = Input(UInt(32.W))
    val out        = Output(UInt(bits.W))
    val carryOut   = Output(UInt(1.W))
  })

  def formRightMask(maskBegin: UInt, bits: Int): UInt = {
    val ret = Wire(UInt(bits.W))
    ret := 0.U
    for (i <- 0 until bits) {
      when (maskBegin === i.asUInt) { ret := ((BigInt(1) << (64-i)).asUInt-1.U) }
    }
    ret
  }

  def formLeftMask(maskEnd: UInt, bits: Int): UInt = {
    val ret = Wire(UInt(bits.W))
    ret := 0.U
    when (maskEnd(6) === 0.U) {
      for (i <- 0 until bits) {
        when (maskEnd === i.asUInt) { ret := (~((BigInt(1) << (63-i)).asUInt(bits.W)-1.U)) }
      }
    }
    ret
  }

  def rotateLeft(n: UInt, shift: UInt): UInt = {
    val ret = Wire(UInt(bits.W))
    ret := ((n ## n) << shift >> 64.U)
    ret
  }

  /* First replicate bottom 32 bits to both halves if 32-bit */
  val rs = Mux(io.is32bit, io.rs(31, 0) ## io.rs(31, 0), io.rs)

  /* Negate shift count for right shifts */
  val rotateCount = Mux(io.rightShift, -io.shift(5, 0), io.shift(5, 0))

  val rotated = rotateLeft(rs, rotateCount)

  /* Trim shift count to 6 bits for 32-bit shifts */
  val sh = (io.shift(6) && ~io.is32bit) ## io.shift(5, 0)

  val mb = Wire(UInt(7.W))
  val me = Wire(UInt(7.W))

  /* Work out mask begin/end indexes (caution, big-endian bit numbering) */
  when (io.clearLeft) {
    when (io.is32bit) {
      mb := 1.U(2.W) ## insn_mb32(io.insn)
    } .otherwise {
      mb := 0.U(1.W) ## insn_mb(io.insn)
    }
  } .elsewhen (io.rightShift) {
    /* this is basically mb <= sh + (is_32bit? 32: 0) */
    when (io.is32bit) {
      mb := sh(5) ## ~sh(5) ## sh(4, 0)
    } .otherwise {
      mb := sh
    }
  } .otherwise {
    mb := 0.U(1.W) ## io.is32bit ## 0.U(5.W)
  }

  when (io.clearRight && io.is32bit) {
    me := 1.U(2.W) ## insn_me32(io.insn)
  } .elsewhen (io.clearRight && !io.clearLeft) {
    me := 0.U(1.W) ## insn_me(io.insn)
  } .otherwise {
    /* effectively, 63 - sh */
    me := sh(6) ## ~sh(5, 0);
  }

  /* Calculate left and right masks */
  val rightMask = formRightMask(mb, bits);
  val leftMask = formLeftMask(me, bits);

  io.carryOut := 0.U

  when ((io.clearLeft && !io.clearRight) || io.rightShift) {
    when (io.signed && rs(63)) {
      io.out := rotated | ~rightMask
      /* Generate carry output for arithmetic shift right of negative value */
      io.carryOut := (io.rs & ~leftMask).orR
    } .otherwise {
      io.out := rotated & rightMask
    }
  } .otherwise {
    /* Mask insert instructions */
    when (io.clearRight &&  mb(5, 0) > me(5, 0)) {
      /* The mask wraps */
      io.out := (rotated & (rightMask | leftMask)) | (io.ra & ~(rightMask | leftMask))
    } .otherwise {
      io.out := (rotated & (rightMask & leftMask)) | (io.ra & ~(rightMask & leftMask))
    }
  }
}

object RotatorObj extends App {
  chisel3.Driver.execute(Array[String](), () => new Rotator(64))
}
