import chisel3._
import chisel3.util._
import chisel3.stage.ChiselStage

import Control._
import Helpers._
import InstructionHelpers._

class Core(bits: Int, memSize: Int, memFileName: String, resetAddr: Int, clockFreq: Int) extends Module {
  val io = IO(new Bundle {
    val tx = Output(UInt(1.W))
    val rx = Input(UInt(1.W))
    val terminate = Output(Bool())
    val ledB = Output(UInt(1.W))
    val ledC = Output(UInt(1.W))
  })

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
  val loadStore = Module(new LoadStore(bits, memWords, clockFreq))
  val control = Module(new Control(bits))

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

  val executeFast = MuxLookup(ctrl.unit, true.B, Array(
    U_MUL -> false.B,
    U_DIV -> false.B
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

  val xerRegisterNum = 1
  val linkRegisterNum = 8
  val CountRegisterNum = 9

  val memSprOut = RegInit(0.U(bits.W))
  when (executeValid && (ctrl.unit === U_SPR)) {
    when (ctrl.internalOp === SPR_MF) {
      when (insn_spr(executeInsn) === linkRegisterNum.asUInt) {
        memSprOut := linkRegister
      } .elsewhen (insn_spr(executeInsn) === CountRegisterNum.asUInt) {
        memSprOut := countRegister
      } .elsewhen (insn_spr(executeInsn) === xerRegisterNum.asUInt) {
        memSprOut := carry << 29
      } .otherwise {
        illegal := true.B
      }
    } .elsewhen (ctrl.internalOp === SPR_MT) {
      when (insn_spr(executeInsn) === linkRegisterNum.asUInt) {
        linkRegister := executeRs
      } .elsewhen (insn_spr(executeInsn) === CountRegisterNum.asUInt) {
        countRegister := executeRs
      } .elsewhen (insn_spr(executeInsn) === xerRegisterNum.asUInt) {
        carry := executeRs(29)
      } .otherwise {
        illegal := true.B
      }
    }
  }

  val memCrOut = RegInit(0.U(bits.W))
  when (executeValid && (ctrl.unit === U_CR)) {
    val fxm = WireDefault(UInt(8.W), 0.U)

    when (ctrl.fxm === FXM_FF) {
      fxm := "hFF".U
    } .elsewhen (ctrl.fxm === FXM) {
      fxm := insn_fxm(executeInsn)
    } .elsewhen (ctrl.fxm === FXM_ONEHOT) {
      val f = insn_fxm_onehot(executeInsn)
      fxm := Mux(f === 0.U, "h80".U, f)
    }

    when (ctrl.internalOp === CR_MF) {
      memCrOut := fxm.asBools.zip(conditionRegister).map({ case (f, c) =>
        Mux(f, c, 0.U)
      }).reduce(_ ## _)
    } .elsewhen (ctrl.internalOp === CR_MT) {
      conditionRegister := fxm.asBools.zip(conditionRegister).zip(executeRs(31, 0).nibbles().reverse).map({ case ((fxm, cr), reg) =>
        Mux(fxm, reg, cr)
      })
    } .elsewhen (ctrl.internalOp === CR_MCRF) {
      conditionRegister(insn_bf(executeInsn)) := conditionRegister(insn_bfa(executeInsn))
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

    when (insn_lr(executeInsn).asBool) {
      linkRegister := executeNia + 4.U
    }

    when (branchTaken) {
      nia.io.redirect := true.B
    }
  }

  val memAdderOut = RegNext(adder.io.out)
  val memAdderCarryOut = RegNext(adder.io.carryOut)
  val memAdderLtOut = RegNext(adder.io.ltOut.asBool)
  val memLogicalOut = RegNext(logical.io.out)
  val memRotatorOut = RegNext(rotator.io.out)
  val memRotatorCarryOut = RegNext(rotator.io.carryOut)
  val memPopulationCountOut = RegNext(populationCount.io.out)
  val memCountZeroesOut = RegNext(countZeroes.io.out)

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

  val memValid = RegNext(executeValid)
  val memFast = RegNext(executeFast && executeValid)

  val memRegValid = RegNext(ctrl.rOut =/= ROUT_NONE)
  val memUnit = RegNext(ctrl.unit)
  val memRegAddr = RegNext(Mux(ctrl.rOut === ROUT_RA, insn_ra(executeInsn), insn_rt(executeInsn)))

  val memRcValid = RegNext((ctrl.compare === CMP_RC_1) || ((ctrl.compare === CMP_RC_RC) && insn_rc(executeInsn).asBool))

  val memCrValid = RegNext(ctrl.compare === CMP_CMP)
  val memCrAddr = RegNext(insn_bf(executeInsn))
  // Compare instructions need to know if a comparison is 32 bit
  val memIs32bit = RegNext(ctrl.is32bit.asBool)

  val memCarryValid = RegNext(ctrl.carryOut.asBool)

  /*
   * Handle load and store with update instructions. We rely on them being single issue
   * and we send the update directly to writeback, bypassing the mem cycle.
   */
  val wbLoadStoreRegAddr = RegInit(insn_ra(executeInsn))
  val wbLoadStoreRegValid = Reg(Bool())
  wbLoadStoreRegValid := false.B
  when (executeValid && (ctrl.unit === U_LDST) && ctrl.update.asBool) {
    wbLoadStoreRegValid := true.B
    wbLoadStoreRegAddr := insn_ra(executeInsn)
  }


  // Memory

  val memRegDataRc = MuxLookup(memUnit, memAdderOut, Array(
    U_LOG -> memLogicalOut,
    U_ROT -> memRotatorOut,
    U_ZER -> memCountZeroesOut,
  ))

  val memRegData = MuxLookup(memUnit, memRegDataRc, Array(
    U_POP -> memPopulationCountOut,
    U_SPR -> memSprOut,
    U_CR  -> memCrOut,
  ))

  val memCarryData = MuxLookup(memUnit, memAdderCarryOut, Array(
    U_ROT -> memRotatorCarryOut
  ))

  val memRcData = cmp(memRegDataRc, memRegDataRc(bits-1).asBool, false.B)

  when (memValid && memCrValid) {
    conditionRegister(memCrAddr) := cmp(memAdderOut, memAdderLtOut, memIs32bit)
  }

  val wbFast = RegNext(memFast)

  val wbCarryValid = RegNext(memCarryValid)
  val wbCarryData = RegNext(memCarryData)

  val wbRcData = RegNext(memRcData)

  // We need to gate these with executeValid because slow units need it
  val wbUnit = RegInit(U_NONE)
  val wbRegValid = RegInit(false.B)
  val wbRegAddr = RegInit(0.U)
  val wbRcValid = RegInit(false.B)
  when (memValid) {
    wbRegValid := memRegValid
    wbUnit := memUnit
    wbRegAddr := memRegAddr
    wbRcValid := memRcValid
  }

  val wbRegData = RegNext(memRegData)


  // Writeback

  val wbRegData1 = MuxLookup(wbUnit, multiplier.io.out.bits, Array(
    U_DIV -> divider.io.out.bits
  ))

  val wbRegData2 = MuxLookup(wbUnit, wbRegData, Array(
    U_MUL -> multiplier.io.out.bits,
    U_DIV -> divider.io.out.bits,
    U_LDST -> loadStore.io.out.bits
  ))

  val wbRcData1 = cmp(wbRegData1, wbRegData1(bits-1).asBool, false.B)

  val wbRcData2 = MuxLookup(wbUnit, wbRcData, Array(
    U_MUL -> wbRcData1,
    U_DIV -> wbRcData1
  ))

  when (wbFast && wbUnit === U_LDST) {
    assert(loadStore.io.out.valid)
  }

  when (wbLoadStoreRegValid) {
    regFile.io.wr(0).bits.addr := wbLoadStoreRegAddr
    regFile.io.wr(0).bits.data := memAdderOut
    regFile.io.wr(0).fire() := true.B
  }. otherwise {
    regFile.io.wr(0).bits.addr := wbRegAddr
    regFile.io.wr(0).bits.data := wbRegData2
    regFile.io.wr(0).fire() := (wbFast && wbRegValid) || multiplier.io.out.valid || divider.io.out.valid
  }

  when (wbFast && wbCarryValid) {
    carry := wbCarryData
  }

  when (wbRcValid && (wbFast || multiplier.io.out.valid || divider.io.out.valid)) {
    conditionRegister(0) := wbRcData2
  }

  val sReset :: sFirst :: sRunning :: Nil = Enum(3)
  val initState = RegInit(sReset)
  switch (initState) {
    is (sReset) {
      initState := sFirst
    }

    is (sFirst) {
      initState := sRunning
    }
  }

  val completed = RegNext((initState === sRunning) && (wbFast || multiplier.io.out.valid || loadStore.io.out.valid || divider.io.out.valid))

  // One instruction in the entire pipeline at a time
  nia.io.nia.ready := completed || (initState === sFirst)
}

object CoreObj extends App {
  (new ChiselStage).emitVerilog(new Core(64, 384*1024, "insns.hex", 0x0, 50000000))
}
