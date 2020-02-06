import chisel3._
import chisel3.util.{Valid, Decoupled, log2Ceil}

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
  val count = Reg(UInt(log2Ceil(bits+1).W))

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
    count := 0.U
  }

  when (busy) {
    when (a(0) === 1.U) {
      res := res + b
    }
    b := b << 1
    a := a >> 1
    count := count + 1.U
  }

  val result = WireDefault(res)
  when (high) {
    when (is32bit) {
      result := res(63, 32) ## res(63, 32)
    } .otherwise {
      result := res(127, 64)
    }
  }

  io.out.bits := RegNext(result)
  io.out.valid := RegNext((count === (bits+1).U) && busy)
  when (io.out.valid) {
    busy := false.B
  }
}

object SimpleMultiplierObj extends App {
  chisel3.Driver.execute(Array[String](), () => new SimpleMultiplier(64))
}
