import chisel3._
import chisel3.util.Decoupled
import chisel3.stage.ChiselStage

class Nia(val bits: Int, val resetAddr: Int) extends Module {
  val io = IO(new Bundle {
    val nia = Decoupled(UInt(bits.W))
    val redirect = Input(Bool())
    val redirect_nia = Input(UInt(bits.W))
  })

  val nia = RegInit(resetAddr.U(bits.W))

  io.nia.valid := false.B

  when (io.redirect) {
    nia := io.redirect_nia
  } .elsewhen (io.nia.ready) {
    nia := nia + 4.U
    io.nia.valid := true.B
  }

  io.nia.bits := nia

}

object NiaObj extends App {
  (new ChiselStage).emitVerilog(new Nia(32, 0x100))
}
