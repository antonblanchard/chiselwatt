import chisel3._

import Helpers._

class ConditionRegisterUnit extends Module {
  val io = IO(new Bundle {
    val fxm = Input(UInt(8.W))
    val rs = Input(UInt(32.W))
    val conditionRegisterIn = Input(Vec(8, UInt(4.W)))
    val conditionRegisterOut = Output(Vec(8, UInt(4.W)))
    val gprOut = Output(UInt(32.W))
  })

  io.gprOut := io.fxm.asBools.zip(io.conditionRegisterIn).map({
    case (f, c) => Mux(f, c, 0.U)
  }).reduce(_ ## _)

  io.conditionRegisterOut := io.fxm.asBools.zip(io.conditionRegisterIn).zip(io.rs.nibbles().reverse).map({
    case ((fxm, cr), reg) => Mux(fxm, reg, cr)
  })
}

object ConditionRegisterUnitObj extends App {
  chisel3.Driver.execute(Array[String](), () => new ConditionRegisterUnit)
}
