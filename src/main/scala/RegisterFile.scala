import chisel3._
import chisel3.util.{log2Ceil, Valid}
import chisel3.stage.ChiselStage

object RegisterFile {
  sealed trait PortDirection
  final case object Read extends PortDirection
  final case object Write extends PortDirection
}

import RegisterFile._

class AddrData(val bits: Int, val rw: PortDirection) extends Bundle {
  val addr   = Input(UInt(log2Ceil(bits+1).W))
  val data   = rw match {
    case Read =>  Output(UInt(bits.W))
    case Write => Input(UInt(bits.W))
  }
}

class RegisterFilePorts(val bits: Int, val numReadPorts: Int, val numWritePorts: Int) extends Bundle {
  val rd = Vec(numReadPorts, new AddrData(bits, Read))
  val wr = Vec(numWritePorts, Flipped(Valid(new AddrData(bits, Write))))
}

class RegisterFile(numRegs: Int, bits: Int, numReadPorts: Int, numWritePorts: Int, bypass: Boolean) extends Module {
  val io = IO(new RegisterFilePorts(bits, numReadPorts, numWritePorts))

  val regs = Mem(numRegs, UInt(bits.W))

  io.rd.foreach{i => i.data := regs.read(i.addr)}
  io.wr.foreach{i => when (i.fire()) { regs.write(i.bits.addr, i.bits.data) } }

  if (bypass) {
    io.rd.foreach{r => io.wr.foreach{w => when (w.fire() && w.bits.addr === r.addr) { r.data := w.bits.data } } }
  }
}

object RegisterFileObj extends App {
  (new ChiselStage).emitVerilog(new RegisterFile(32, 64, 3, 1, true))
}
