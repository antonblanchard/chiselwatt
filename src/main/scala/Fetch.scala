import chisel3._
import chisel3.util.log2Ceil

class Fetch(val bits: Int, val words: Int) extends Module {
  val io = IO(new Bundle {
    val nia = Input(UInt(bits.W))
    val insn = Output(UInt(32.W))
    val mem = new MemoryPort(bits, words, false)
  })

  io.mem.addr := io.nia >> log2Ceil(bits/8)

  /* Issues with conditional entries in ports */
  io.mem.writeEnable := false.B
  io.mem.writeMask := 0.U
  io.mem.writeData := 0.U

  val niaNext = RegNext(io.nia)

  if (bits == 64) {
    when (niaNext(2).asBool) {
      io.insn := io.mem.readData(63, 32)
    } .otherwise {
      io.insn := io.mem.readData(31, 0)
    }
  } else {
    io.insn := io.mem.readData(31, 0)
  }
}
