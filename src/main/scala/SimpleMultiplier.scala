import chisel3._
import chisel3.util.{Valid, Decoupled}

import Helpers._

class MultiplierInput(val bits: Int) extends Bundle {
  val a = UInt(bits.W)
  val b = UInt(bits.W)
  val is32bit = Bool()
  val signed = Bool()
  val high = Bool()
}

class SimpleMultiplier(val bits: Int) extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(Decoupled(new MultiplierInput(bits)))
    val out = Output(Valid(UInt(bits.W)))
  })

  val a = Reg(UInt((2*bits).W))
  val b = Reg(UInt((2*bits).W))
  val is32bit = Reg(Bool())
  val high = Reg(Bool())
  val res = Reg(UInt((2*bits).W))
  val busy = RegInit(false.B)

  io.in.ready := !busy

  when (io.in.valid && !busy) {
    when (io.in.bits.is32bit) {
      when (io.in.bits.signed) {
        a := io.in.bits.a.signExtend(32, 2*bits)
        b := io.in.bits.b.signExtend(32, 2*bits)
      } .otherwise {
        a := io.in.bits.a(31, 0)
        b := io.in.bits.b(31, 0)
      }
    } .otherwise {
      when (io.in.bits.signed) {
        a := io.in.bits.a.signExtend(64, 2*bits)
        b := io.in.bits.b.signExtend(64, 2*bits)
      } .otherwise {
        a := io.in.bits.a
        b := io.in.bits.b
      }
    }

    is32bit := io.in.bits.is32bit
    high := io.in.bits.high
    res := 0.U
    busy := true.B
  }

  when (busy) {
    when (a(0) === 1.U) {
      res := res + b
    }
    b := b << 1
    a := a >> 1
  }

  io.out.bits := res
  when (high) {
    when (is32bit) {
      io.out.bits := res(63, 32) ## res(63, 32)
    } .otherwise {
      io.out.bits := res(127, 64)
    }
  }

  io.out.valid := (a === 0.U) && busy
  when (io.out.valid) {
    busy := false.B
  }
}

object SimpleMultiplierObj extends App {
  chisel3.Driver.execute(Array[String](), () => new SimpleMultiplier(64))
}
