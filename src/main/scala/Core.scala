import chisel3._
import chisel3.util._

import Control._
import Helpers._
import InstructionHelpers._

class Core(bits: Int, memSize: Int, memFileName: String, resetAddr: Int) extends Module {
  val io = IO(new Bundle {
    val tx = Output(UInt(1.W))
    val rx = Input(UInt(1.W))
    val terminate = Output(Bool())
    val ledB = Output(UInt(1.W))
    val ledC = Output(UInt(1.W))
  })

  val memWords = memSize/(bits/8)

  val nia = Module(new Nia(bits, resetAddr))
  val fetch = Module(new Fetch(bits, memWords))
  val adder = Module(new Adder(bits))
  val logical = Module(new Logical(bits))
  val rotator = Module(new Rotator(bits))
  val populationCount = Module(new PopulationCount(bits))
  val countZeroes = Module(new CountZeroes(bits))
  val multiplier = Module(new SimpleMultiplier(bits))
  val divider = Module(new SimpleDivider(bits))
  val mem = Module(new MemoryBlackBoxWrapper(bits, memWords, memFileName))
  val loadStore = Module(new LoadStore(bits, memWords))
  val control = Module(new Control(bits))
  val conditionRegisterUnit = Module(new ConditionRegisterUnit)

  val regFile = Module(new RegisterFile(32, bits, 3, 1, false))
  val carry = RegInit(0.U(1.W))
  val conditionRegister = RegInit(VecInit(Seq.fill(8)(0.U(4.W))))
  val linkRegister = RegInit(0.U(bits.W))
  val countRegister = RegInit(0.U(bits.W))

  val illegal = WireDefault(false.B)

  mem.io.loadStorePort <> loadStore.io.mem
  mem.io.fetchPort <> fetch.io.mem

  nia.io.redirect := false.B
  nia.io.redirect_nia := 0.U

  /* Blink an LED, this proves we are out of reset */
  val led = RegInit(0.U(1.W))
  val (counterValue, counterWrap) = Counter(true.B, 50000000)
  when (counterWrap) {
    led := ~led
  }
  io.ledB := led
  io.ledC := 1.U

  io.tx := loadStore.io.tx
  loadStore.io.rx := io.rx

  fetch.io.nia := nia.io.nia.bits

  val decodeInsn = RegNext(fetch.io.insn)
  val decodeNia = RegNext(nia.io.nia.bits)
  val decodeValid = RegNext(nia.io.nia.valid)

  // Decode

  control.io.insn := decodeInsn

  regFile.io.rd(0).addr := insn_ra(decodeInsn)
  regFile.io.rd(1).addr := insn_rb(decodeInsn)
  regFile.io.rd(2).addr := insn_rs(decodeInsn)

  val decodeRa = MuxCase(regFile.io.rd(0).data, Seq(
    (control.io.out.rA === RA_ZERO) -> 0.U(bits.W),
    ((control.io.out.rA === RA_RA_OR_ZERO) && (insn_ra(decodeInsn) === 0.U)) -> 0.U(bits.W)
  ))

  val decodeRb = MuxLookup(control.io.out.rB, regFile.io.rd(1).data, Array(
    RB_CONST_UI -> insn_ui(decodeInsn),
    RB_CONST_SI -> insn_si(decodeInsn).signExtend(16, bits),
    RB_CONST_UI_HI -> insn_ui(decodeInsn) ## 0.U(16.W),
    RB_CONST_SI_HI -> (insn_si(decodeInsn) ## 0.U(16.W)).signExtend(32, bits),
    RB_CONST_DS -> (insn_ds(decodeInsn) ## 0.U(2.W)).signExtend(16, bits),
    RB_CONST_M1 -> Fill(bits, 1.U),
    RB_CONST_ZERO -> 0.U(bits.W),
    RB_CONST_SH -> insn_sh(decodeInsn),
    RB_CONST_SH32 -> insn_sh32(decodeInsn)
  ))

  val decodeRs = regFile.io.rd(2).data

  val decodeCarry = MuxLookup(control.io.out.carryIn, carry, Array(
    CA_0 -> 0.U,
    CA_1 -> 1.U
  ))

  val ctrl = RegNext(control.io.out)
  val executeInsn = RegNext(decodeInsn)
  val executeRa = RegNext(decodeRa)
  val executeRb = RegNext(decodeRb)
  val executeRs = RegNext(decodeRs)
  val executeCarry = RegNext(decodeCarry)
  val executeValid = RegNext(decodeValid)
  val executeNia = RegNext(decodeNia)

  // Execute

  val executeFastOp = MuxLookup(ctrl.unit, true.B, Array(
    U_MUL -> false.B,
    U_DIV -> false.B,
    U_LDST -> false.B
  ))

  adder.io.a := executeRa
  adder.io.b := executeRb
  adder.io.carryIn := executeCarry
  adder.io.invertIn := ctrl.invertIn
  adder.io.is32bit := ctrl.is32bit
  adder.io.signed := ctrl.signed

  logical.io.a := executeRs
  logical.io.b := executeRb
  logical.io.internalOp := ctrl.internalOp
  logical.io.invertIn := ctrl.invertIn
  logical.io.invertOut := ctrl.invertOut
  logical.io.length := ctrl.length

  rotator.io.ra := executeRa
  rotator.io.shift := executeRb
  rotator.io.rs := executeRs
  rotator.io.is32bit := ctrl.is32bit
  rotator.io.signed := ctrl.signed
  rotator.io.rightShift := ctrl.rightShift
  rotator.io.clearLeft := ctrl.clearLeft
  rotator.io.clearRight := ctrl.clearRight
  rotator.io.insn := executeInsn

  populationCount.io.a := executeRs
  populationCount.io.length := ctrl.length

  countZeroes.io.a := executeRs
  countZeroes.io.countRight := ctrl.countRight
  countZeroes.io.is32bit := ctrl.is32bit

  multiplier.io.in.bits.a := executeRa
  multiplier.io.in.bits.b := executeRb
  multiplier.io.in.bits.is32bit := ctrl.is32bit
  multiplier.io.in.bits.signed := ctrl.signed
  multiplier.io.in.bits.high := ctrl.high
  multiplier.io.in.valid := false.B
  when (executeValid && (ctrl.unit === U_MUL)) {
    multiplier.io.in.valid := true.B
  }

  divider.io.in.bits.dividend := executeRa
  divider.io.in.bits.divisor := executeRb
  divider.io.in.bits.is32bit := ctrl.is32bit
  divider.io.in.bits.signed := ctrl.signed
  divider.io.in.bits.extended := ctrl.extended
  divider.io.in.bits.modulus := 0.U // fixme
  divider.io.in.valid := false.B
  when (executeValid && (ctrl.unit === U_DIV)) {
    divider.io.in.valid := true.B
  }

  loadStore.io.in.bits.a := executeRa
  loadStore.io.in.bits.b := executeRb
  loadStore.io.in.bits.data := executeRs
  loadStore.io.in.bits.internalOp := ctrl.internalOp
  loadStore.io.in.bits.length := ctrl.length
  loadStore.io.in.bits.signed := ctrl.signed
  loadStore.io.in.bits.byteReverse := ctrl.byteReverse
  loadStore.io.in.bits.update := ctrl.update
  loadStore.io.in.bits.reservation := ctrl.reservation
  loadStore.io.in.valid := false.B
  when (executeValid && (ctrl.unit === U_LDST)) {
    loadStore.io.in.valid := true.B
  }

  val fxm = MuxLookup(ctrl.fxm, "hFF".U, Array(
    FXM -> insn_fxm(executeInsn),
    FXM_ONEHOT -> { val f = insn_fxm_onehot(executeInsn); Mux(f === 0.U, "h80".U, f) }
  ))

  conditionRegisterUnit.io.fxm := fxm
  conditionRegisterUnit.io.rs := executeRs
  conditionRegisterUnit.io.conditionRegisterIn := conditionRegister

  val xerRegisterNum = 1.U
  val linkRegisterNum = 8.U
  val countRegisterNum = 9.U

  val sprOut = RegInit(0.U(bits.W))
  sprOut := MuxLookup(insn_spr(executeInsn), 0.U, Seq(
    linkRegisterNum -> linkRegister,
    countRegisterNum -> countRegister,
    xerRegisterNum -> (carry << 29.U)
  ))

  when (executeValid && (ctrl.unit === U_SPR)) {
    when (ctrl.internalOp === SPR_MT) {
      when (insn_spr(executeInsn) === linkRegisterNum.asUInt) {
        linkRegister := executeRs
      } .elsewhen (insn_spr(executeInsn) === countRegisterNum.asUInt) {
        countRegister := executeRs
      } .elsewhen (insn_spr(executeInsn) === xerRegisterNum.asUInt) {
        carry := executeRs(29)
      } .otherwise {
        illegal := true.B
      }
    }
  }

  when (executeValid && (ctrl.unit === U_BR)) {
    val branchTaken = WireDefault(Bool(), false.B)

    val target = MuxCase((insn_bd(executeInsn) ## 0.U(2.W)).signExtend(16, bits) + executeNia, Seq(
      (ctrl.internalOp === BR_UNCOND) -> ((insn_li(executeInsn) ## 0.U(2.W)).signExtend(26, bits) + executeNia),
      (ctrl.brTarget === BR_TARGET_CTR) -> countRegister,
      (ctrl.brTarget === BR_TARGET_LR) -> linkRegister,
    ))
    nia.io.redirect_nia := target

    when (ctrl.internalOp === BR_UNCOND) {
      branchTaken := true.B
    } .otherwise {
      val bo = insn_bo(executeInsn)
      val bi = insn_bi(executeInsn)
      val cnt = WireDefault(countRegister)
      val counterIsTarget = (ctrl.brTarget === BR_TARGET_CTR)

      when ((counterIsTarget === 0.U) && (bo(4-2) === 0.U)) {
        cnt := countRegister - 1.U
        countRegister := cnt
      }

      val crBit = (conditionRegister.reduce(_ ## _))(31.U-bi)
      val crBitMatch = (crBit === bo(4-1))
      val ctrOk = (bo(4-2).asBool || (cnt.orR ^ bo(4-3)).asBool)
      val condOk = bo(4-0).asBool || crBitMatch

      when ((counterIsTarget || ctrOk) && condOk) {
        branchTaken := true.B
      }
    }

    when (branchTaken) {
      nia.io.redirect := true.B
      when (insn_lr(executeInsn).asBool) {
        linkRegister := executeNia + 4.U
      }
    }
  }

  val adderOut = RegNext(adder.io.out)
  val adderCarryOut = RegNext(adder.io.carryOut)
  val adderLtOut = RegNext(adder.io.ltOut.asBool)
  val logicalOut = RegNext(logical.io.out)
  val rotatorOut = RegNext(rotator.io.out)
  val rotatorCarryOut = RegNext(rotator.io.carryOut)
  val populationCountOut = RegNext(populationCount.io.out)
  val countZeroesOut = RegNext(countZeroes.io.out)
  val conditionRegisterGprOut = RegNext(conditionRegisterUnit.io.gprOut)
  val conditionRegisterCrOut = RegNext(conditionRegisterUnit.io.conditionRegisterOut)

  when (executeValid && (ctrl.unit === U_ILL)) {
    illegal := true.B
  }

  io.terminate := false.B
  when (illegal) {
    printf("ILLEGAL (%x) at %x\n", executeInsn, executeNia)
    io.terminate := true.B
    /* Send it into an infinite loop */
    nia.io.redirect := true.B
    nia.io.redirect_nia := executeNia
  }

  /* Handle load and store with update instructions */
  val writebackLoadStoreAddr = RegNext(insn_ra(executeInsn))
  val writebackLoadStore = RegInit(false.B)
  writebackLoadStore := false.B
  when (executeValid && (ctrl.unit === U_LDST) && ctrl.update.asBool) {
    writebackLoadStore := true.B
  }

  val writebackFastValid = RegNext(executeValid && executeFastOp)
  val writebackFastCarryValid = RegNext(ctrl.carryOut.asBool)

  // We need to gate these with executeValid because slow units need it
  val writebackUnit = RegInit(U_NONE)
  val writebackRValid = RegInit(false.B)
  val writebackAddr = RegInit(0.U)
  val writebackRc = RegInit(false.B)
  when (executeValid) {
    writebackUnit := ctrl.unit
    writebackRValid := ctrl.rOut =/= ROUT_NONE
    writebackAddr := Mux(ctrl.rOut === ROUT_RA, insn_ra(executeInsn), insn_rt(executeInsn))
    writebackRc := (ctrl.compare === CMP_RC_1) || ((ctrl.compare === CMP_RC_RC) && insn_rc(executeInsn).asBool)
  }

  val writebackConditionRegisterWrite = RegNext(executeValid && (ctrl.unit === U_CR) && (ctrl.crOut === true.B))
  val writebackCmp = RegNext(ctrl.compare === CMP_CMP)
  val writebackCrField = RegNext(insn_bf(executeInsn))
  // Compare instructions need to know if a comparison is 32 bit
  val writebackIs32bit = RegNext(ctrl.is32bit.asBool)

  // Writeback

  val wrData = MuxLookup(writebackUnit, adderOut, Array(
                  U_LOG -> logicalOut,
                  U_ROT -> rotatorOut,
                  U_POP -> populationCountOut,
                  U_ZER -> countZeroesOut,
                  U_SPR -> sprOut,
                  U_CR  -> conditionRegisterGprOut,
                  U_MUL -> multiplier.io.out.bits,
                  U_DIV -> divider.io.out.bits,
                  U_LDST -> loadStore.io.out.bits))

  when (writebackLoadStore) {
    regFile.io.wr(0).bits.addr := writebackLoadStoreAddr
    regFile.io.wr(0).bits.data := adderOut
    regFile.io.wr(0).fire() := true.B
  }. otherwise {
    regFile.io.wr(0).bits.addr := writebackAddr
    regFile.io.wr(0).bits.data := wrData
    when (multiplier.io.out.valid || divider.io.out.valid || (loadStore.io.out.valid && writebackRValid)) {
      regFile.io.wr(0).fire() := true.B
    } .otherwise {
      regFile.io.wr(0).fire() := writebackFastValid && writebackRValid
    }
  }

  when (writebackFastValid && writebackFastCarryValid) {
    carry := MuxLookup(writebackUnit, adderCarryOut, Array(
      U_ROT -> rotatorCarryOut
    ))
  }

  private def cmp(res: UInt, lt: Bool, is32bit: Bool): UInt = {
    val isZero = Mux(is32bit, res(31, 0) === 0.U, res === 0.U)

    val crLt   = "b1000".U
    val crGt   = "b0100".U
    val crZero = "b0010".U

    MuxCase(crGt, Seq(
      isZero -> crZero,
      lt -> crLt
    ))
  }

  when (writebackRc && (writebackFastValid || multiplier.io.out.valid || divider.io.out.valid)) {
    conditionRegister(0) := cmp(wrData, wrData(bits-1).asBool, false.B)
  } .elsewhen (writebackFastValid && writebackCmp) {
    conditionRegister(writebackCrField) :=
      cmp(wrData, adderLtOut, writebackIs32bit)
  } .elsewhen (writebackConditionRegisterWrite) {
    conditionRegister := conditionRegisterCrOut
  }

  val completed = RegNext(writebackFastValid || multiplier.io.out.valid || loadStore.io.out.valid || divider.io.out.valid)

  val sFirst :: sSecond :: sThird :: Nil = Enum(3)
  val initState = RegInit(sFirst)
  switch (initState) {
    is (sFirst) {
      initState := sSecond
    }

    is (sSecond) {
      initState := sThird
    }
  }

  // One instruction in the entire pipeline at a time
  nia.io.nia.ready := completed || (initState === sSecond)
}

object CoreObj extends App {
  chisel3.Driver.execute(Array[String](), () => new Core(64, 384*1024, "insns.hex", 0x0))
}
