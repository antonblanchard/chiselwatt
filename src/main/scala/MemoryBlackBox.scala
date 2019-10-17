import chisel3._
import chisel3.util.{log2Ceil,HasBlackBoxInline}

class MemoryBlackBox(val bits: Int, val words: Int, val filename: String) extends BlackBox with HasBlackBoxInline {
  val io = IO(new Bundle() {
    val clock      = Input(Clock())

    val readEnable1  = Input(Bool())
    val writeEnable1 = Input(Bool())
    val writeMask1   = Input(UInt((bits/8).W))
    val addr1        = Input(UInt(log2Ceil(words).W))
    val readData1    = Output(UInt(bits.W))
    val writeData1   = Input(UInt(bits.W))

    val readEnable2 = Input(Bool())
    val readAddr2   = Input(UInt(log2Ceil(words).W))
    val readData2   = Output(UInt(bits.W))
  })

  setInline("MemoryBlackBox.v",
    s"""
      |module MemoryBlackBox #(
      |    parameter BITS = $bits,
      |    parameter WORDS = $words
      |) (
      |    input clock,
      |
      |    input readEnable1,
      |    input writeEnable1,
      |    input [BITS/8-1:0] writeMask1,
      |    input [$$clog2(WORDS)-1:0] addr1,
      |    output logic [BITS-1:0] readData1,
      |    input [BITS-1:0] writeData1,
      |
      |    input readEnable2,
      |    input [$$clog2(WORDS)-1:0] readAddr2,
      |    output logic [BITS-1:0] readData2
      |);
      |
      |integer i;
      |logic [BITS-1:0] ram[0:WORDS-1];
      |
      |always_ff@(posedge clock)
      |begin
      |    if (writeEnable1)
      |    begin
      |      for (i = 0; i < BITS/8; i = i + 1)
      |      begin
      |        if (writeMask1[i]) ram[addr1][i*8+:8] <= writeData1[i*8+:8];
      |      end
      |    end
      |    else if (readEnable1)
      |    begin
      |       readData1 <= ram[addr1];
      |    end
      |
      |    if (readEnable2) readData2 <= ram[readAddr2];
      |end
      |initial begin
      |    $$readmemh("$filename", ram);
      |end
      |endmodule
      |""".stripMargin)
}

class MemoryPort(val bits: Int, val words: Int, val rw: Boolean) extends Bundle {
  val addr        = Output(UInt(log2Ceil(words).W))
  val readEnable  = Output(Bool())
  val readData    = Input(UInt(bits.W))

  //val writeEnable = if (rw) Some(Output(Bool())) else None
  //val writeMask   = if (rw) Some(Output(UInt((bits/8).W))) else None
  //val writeData   = if (rw) Some(Output(UInt(bits.W))) else None
  val writeEnable = (Output(Bool()))
  val writeMask   = (Output(UInt((bits/8).W)))
  val writeData   = (Output(UInt(bits.W)))
}

class MemoryBlackBoxWrapper(val bits: Int, val words: Int, val filename: String) extends Module {
  val io = IO(new Bundle() {
    val loadStorePort = Flipped(new MemoryPort(bits, words, true))
    val fetchPort = Flipped(new MemoryPort(bits, words, false))
  })

  val m = Module(new MemoryBlackBox(bits, words, filename))

  m.io.clock := clock

  m.io.readEnable1 := io.loadStorePort.readEnable
  m.io.writeEnable1 := io.loadStorePort.writeEnable
  m.io.writeMask1 := io.loadStorePort.writeMask
  m.io.addr1 := io.loadStorePort.addr
  io.loadStorePort.readData := m.io.readData1
  m.io.writeData1 := io.loadStorePort.writeData

  m.io.readEnable2 := io.fetchPort.readEnable
  m.io.readAddr2 := io.fetchPort.addr
  io.fetchPort.readData := m.io.readData2
}

object MemoryBlackBoxObj extends App {
  chisel3.Driver.execute(Array[String](), () => new MemoryBlackBoxWrapper(64, 1024, "test.hex"))
}
