import chisel3._
import chisel3.util.{ListLookup}

object Control {
  val Y = true.B
  val N = false.B

  val DC = false.B

  // Unit
  val U_ADD  = 0.U(4.W)
  val U_LOG  = 1.U(4.W)
  val U_ROT  = 2.U(4.W)
  val U_MUL  = 3.U(4.W)
  val U_DIV  = 4.U(4.W)
  val U_LDST = 5.U(4.W)
  val U_BR   = 6.U(4.W)
  val U_CR   = 7.U(4.W)
  val U_ZER  = 8.U(4.W)
  val U_POP  = 9.U(4.W)
  val U_SPR  = 10.U(4.W)
  val U_ILL  = 11.U(4.W)
  val U_NONE = 12.U(4.W)

  // Internal ops
  val LOG_AND  = 0.U(2.W)
  val LOG_OR   = 1.U(2.W)
  val LOG_XOR  = 2.U(2.W)
  val LOG_EXTS = 3.U(2.W)

  val LDST_LOAD  = 0.U(2.W)
  val LDST_STORE = 1.U(2.W)

  val DIV_DIV = 0.U(2.W)
  val DIV_MOD = 1.U(2.W)

  val SPR_MF = 0.U(2.W)
  val SPR_MT = 1.U(2.W)

  val CR_MF = 0.U(2.W)
  val CR_MT = 1.U(2.W)

  val BR_UNCOND = 0.U(2.W)
  val BR_COND   = 1.U(2.W)

  // Input registers
  val RA_RA         = 1.U(2.W)
  val RA_RA_OR_ZERO = 2.U(2.W)
  val RA_ZERO       = 3.U(2.W)

  val RB_RB          = 1.U(4.W)
  val RB_CONST_UI    = 2.U(4.W)
  val RB_CONST_SI    = 3.U(4.W)
  val RB_CONST_SI_HI = 4.U(4.W)
  val RB_CONST_UI_HI = 5.U(4.W)
  val RB_CONST_DS    = 6.U(4.W)
  val RB_CONST_M1    = 7.U(4.W)
  val RB_CONST_ZERO  = 8.U(4.W)
  val RB_CONST_SH    = 9.U(4.W)
  val RB_CONST_SH32  = 10.U(4.W)

  val RS_RS = 1.U(1.W)

  // Output registers
  val ROUT_NONE = 0.U(2.W)
  val ROUT_RT   = 1.U(2.W)
  val ROUT_RA   = 2.U(2.W)

  val CMP_RC_0  = 0.U(2.W)
  val CMP_RC_RC = 1.U(2.W)
  val CMP_RC_1  = 2.U(2.W)
  val CMP_CMP   = 3.U(2.W)

  val CA_0  = 0.U(2.W)
  val CA_CA = 1.U(2.W)
  val CA_1  = 2.U(2.W)

  val LEN_1B = 0.U(2.W)
  val LEN_2B = 1.U(2.W)
  val LEN_4B = 2.U(2.W)
  val LEN_8B = 3.U(2.W)

  val FXM        = 0.U(2.W)
  val FXM_FF     = 1.U(2.W)
  val FXM_ONEHOT = 2.U(2.W)

  val BR_TARGET_NONE = 0.U(2.W)
  val BR_TARGET_CTR  = 1.U(2.W)
  val BR_TARGET_LR   = 2.U(2.W)

  import Instructions._

  val default =
                     List(U_ILL,  DC,         DC,            DC,             DC,    ROUT_NONE, DC,     DC,      DC,  DC,   CMP_RC_0,  DC,     DC,   DC,       DC,       DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC)

  val map = Array(
                       // unit    internalOp  rA             rB              rS     rOut       carryIn carryOut crIn crOut compare    is32bit signed invertIn invertOut rightShift clearLeft clearRight length  byteReverse update reservation high extended countRight fxm         brTarget
    ADDIC         -> List(U_ADD,  DC,         RA_RA,         RB_CONST_SI,    DC,    ROUT_RT,   CA_0,   Y,       DC,  DC,   CMP_RC_0,  DC,     DC,    N,       DC,       DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC),
    ADDIC_DOT     -> List(U_ADD,  DC,         RA_RA,         RB_CONST_SI,    DC,    ROUT_RT,   CA_0,   Y,       DC,  DC,   CMP_RC_1,  DC,     DC,    N,       DC,       DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC),
    ADDI          -> List(U_ADD,  DC,         RA_RA_OR_ZERO, RB_CONST_SI,    DC,    ROUT_RT,   CA_0,   N,       DC,  DC,   CMP_RC_0,  DC,     DC,    N,       DC,       DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC),
    ADDIS         -> List(U_ADD,  DC,         RA_RA_OR_ZERO, RB_CONST_SI_HI, DC,    ROUT_RT,   CA_0,   N,       DC,  DC,   CMP_RC_0,  DC,     DC,    N,       DC,       DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC),
    SUBFIC        -> List(U_ADD,  DC,         RA_RA,         RB_CONST_SI,    DC,    ROUT_RT,   CA_1,   Y,       DC,  DC,   CMP_RC_0,  DC,     DC,    Y,       DC,       DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC),
    ADD           -> List(U_ADD,  DC,         RA_RA,         RB_RB,          DC,    ROUT_RT,   CA_0,   N,       DC,  DC,   CMP_RC_RC, DC,     DC,    N,       DC,       DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC),
    ADDC          -> List(U_ADD,  DC,         RA_RA,         RB_RB,          DC,    ROUT_RT,   CA_0,   Y,       DC,  DC,   CMP_RC_RC, DC,     DC,    N,       DC,       DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC),
    ADDE          -> List(U_ADD,  DC,         RA_RA,         RB_RB,          DC,    ROUT_RT,   CA_CA,  Y,       DC,  DC,   CMP_RC_RC, DC,     DC,    N,       DC,       DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC),
    ADDME         -> List(U_ADD,  DC,         RA_RA,         RB_CONST_M1,    DC,    ROUT_RT,   CA_CA,  Y,       DC,  DC,   CMP_RC_RC, DC,     DC,    N,       DC,       DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC),
    ADDZE         -> List(U_ADD,  DC,         RA_RA,         RB_CONST_ZERO,  DC,    ROUT_RT,   CA_CA,  Y,       DC,  DC,   CMP_RC_RC, DC,     DC,    N,       DC,       DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC),
    SUBF          -> List(U_ADD,  DC,         RA_RA,         RB_RB,          DC,    ROUT_RT,   CA_1,   N,       DC,  DC,   CMP_RC_RC, DC,     DC,    Y,       DC,       DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC),
    SUBFC         -> List(U_ADD,  DC,         RA_RA,         RB_RB,          DC,    ROUT_RT,   CA_1,   Y,       DC,  DC,   CMP_RC_RC, DC,     DC,    Y,       DC,       DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC),
    SUBFE         -> List(U_ADD,  DC,         RA_RA,         RB_RB,          DC,    ROUT_RT,   CA_CA,  Y,       DC,  DC,   CMP_RC_RC, DC,     DC,    Y,       DC,       DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC),
    SUBFME        -> List(U_ADD,  DC,         RA_RA,         RB_CONST_M1,    DC,    ROUT_RT,   CA_CA,  Y,       DC,  DC,   CMP_RC_RC, DC,     DC,    Y,       DC,       DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC),
    SUBFZE        -> List(U_ADD,  DC,         RA_RA,         RB_CONST_ZERO,  DC,    ROUT_RT,   CA_CA,  Y,       DC,  DC,   CMP_RC_RC, DC,     DC,    Y,       DC,       DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC),
    NEG           -> List(U_ADD,  DC,         RA_RA,         RB_CONST_ZERO,  DC,    ROUT_RT,   CA_1,   N,       DC,  DC,   CMP_RC_RC, DC,     DC,    Y,       N,        DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC),
    ANDI_DOT      -> List(U_LOG,  LOG_AND,    DC,            RB_CONST_UI,    RS_RS, ROUT_RA,   DC,     DC,      DC,  DC,   CMP_RC_1,  DC,     DC,    N,       N,        DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC),
    ANDIS_DOT     -> List(U_LOG,  LOG_AND,    DC,            RB_CONST_UI_HI, RS_RS, ROUT_RA,   DC,     DC,      DC,  DC,   CMP_RC_1,  DC,     DC,    N,       N,        DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC),
    AND           -> List(U_LOG,  LOG_AND,    DC,            RB_RB,          RS_RS, ROUT_RA,   DC,     DC,      DC,  DC,   CMP_RC_RC, DC,     DC,    N,       N,        DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC),
    ANDC          -> List(U_LOG,  LOG_AND,    DC,            RB_RB,          RS_RS, ROUT_RA,   DC,     DC,      DC,  DC,   CMP_RC_RC, DC,     DC,    Y,       N,        DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC),
    NAND          -> List(U_LOG,  LOG_AND,    DC,            RB_RB,          RS_RS, ROUT_RA,   DC,     DC,      DC,  DC,   CMP_RC_RC, DC,     DC,    N,       Y,        DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC),
    ORI           -> List(U_LOG,  LOG_OR,     DC,            RB_CONST_UI,    RS_RS, ROUT_RA,   DC,     DC,      DC,  DC,   CMP_RC_0,  DC,     DC,    N,       N,        DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC),
    ORIS          -> List(U_LOG,  LOG_OR,     DC,            RB_CONST_UI_HI, RS_RS, ROUT_RA,   DC,     DC,      DC,  DC,   CMP_RC_0,  DC,     DC,    N,       N,        DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC),
    NOR           -> List(U_LOG,  LOG_OR,     DC,            RB_RB,          RS_RS, ROUT_RA,   DC,     DC,      DC,  DC,   CMP_RC_RC, DC,     DC,    N,       Y,        DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC),
    OR            -> List(U_LOG,  LOG_OR,     DC,            RB_RB,          RS_RS, ROUT_RA,   DC,     DC,      DC,  DC,   CMP_RC_RC, DC,     DC,    N,       N,        DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC),
    ORC           -> List(U_LOG,  LOG_OR,     DC,            RB_RB,          RS_RS, ROUT_RA,   DC,     DC,      DC,  DC,   CMP_RC_RC, DC,     DC,    Y,       N,        DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC),
    XORI          -> List(U_LOG,  LOG_XOR,    DC,            RB_CONST_UI,    RS_RS, ROUT_RA,   DC,     DC,      DC,  DC,   CMP_RC_0,  DC,     DC,    N,       N,        DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC),
    XORIS         -> List(U_LOG,  LOG_XOR,    DC,            RB_CONST_UI_HI, RS_RS, ROUT_RA,   DC,     DC,      DC,  DC,   CMP_RC_0,  DC,     DC,    N,       N,        DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC),
    EQV           -> List(U_LOG,  LOG_XOR,    DC,            RB_RB,          RS_RS, ROUT_RA,   DC,     DC,      DC,  DC,   CMP_RC_RC, DC,     DC,    N,       Y,        DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC),
    XOR           -> List(U_LOG,  LOG_XOR,    DC,            RB_RB,          RS_RS, ROUT_RA,   DC,     DC,      DC,  DC,   CMP_RC_RC, DC,     DC,    N,       N,        DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC),
    EXTSB         -> List(U_LOG,  LOG_EXTS,   DC,            RB_RB,          RS_RS, ROUT_RA,   DC,     DC,      DC,  DC,   CMP_RC_RC, DC,     DC,    N,       N,        DC,        DC,       DC,        LEN_1B, DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC),
    EXTSH         -> List(U_LOG,  LOG_EXTS,   DC,            RB_RB,          RS_RS, ROUT_RA,   DC,     DC,      DC,  DC,   CMP_RC_RC, DC,     DC,    N,       N,        DC,        DC,       DC,        LEN_2B, DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC),
    EXTSW         -> List(U_LOG,  LOG_EXTS,   DC,            RB_RB,          RS_RS, ROUT_RA,   DC,     DC,      DC,  DC,   CMP_RC_RC, DC,     DC,    N,       N,        DC,        DC,       DC,        LEN_4B, DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC),
    RLWIMI        -> List(U_ROT,  DC,         RA_RA,         RB_CONST_SH32,  RS_RS, ROUT_RA,   DC,     N,       DC,  DC,   CMP_RC_RC, Y,      N,     DC,      DC,       N,         Y,        Y,         DC,     DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC),
    RLWINM        -> List(U_ROT,  DC,         RA_ZERO,       RB_CONST_SH32,  RS_RS, ROUT_RA,   DC,     N,       DC,  DC,   CMP_RC_RC, Y,      N,     DC,      DC,       N,         Y,        Y,         DC,     DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC),
    RLWNM         -> List(U_ROT,  DC,         RA_ZERO,       RB_RB,          RS_RS, ROUT_RA,   DC,     N,       DC,  DC,   CMP_RC_RC, Y,      N,     DC,      DC,       N,         Y,        Y,         DC,     DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC),
    RLDIC         -> List(U_ROT,  DC,         RA_ZERO,       RB_CONST_SH,    RS_RS, ROUT_RA,   DC,     N,       DC,  DC,   CMP_RC_RC, N,      N,     DC,      DC,       N,         Y,        Y,         DC,     DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC),
    RLDICL        -> List(U_ROT,  DC,         RA_ZERO,       RB_CONST_SH,    RS_RS, ROUT_RA,   DC,     N,       DC,  DC,   CMP_RC_RC, N,      N,     DC,      DC,       N,         Y,        N,         DC,     DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC),
    RLDICR        -> List(U_ROT,  DC,         RA_ZERO,       RB_CONST_SH,    RS_RS, ROUT_RA,   DC,     N,       DC,  DC,   CMP_RC_RC, N,      N,     DC,      DC,       N,         N,        Y,         DC,     DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC),
    RLDIMI        -> List(U_ROT,  DC,         RA_RA,         RB_CONST_SH,    RS_RS, ROUT_RA,   DC,     N,       DC,  DC,   CMP_RC_RC, N,      N,     DC,      DC,       N,         Y,        Y,         DC,     DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC),
    RLDCL         -> List(U_ROT,  DC,         RA_ZERO,       RB_RB,          RS_RS, ROUT_RA,   DC,     N,       DC,  DC,   CMP_RC_RC, N,      N,     DC,      DC,       N,         Y,        N,         DC,     DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC),
    RLDCR         -> List(U_ROT,  DC,         RA_ZERO,       RB_RB,          RS_RS, ROUT_RA,   DC,     N,       DC,  DC,   CMP_RC_RC, N,      N,     DC,      DC,       N,         N,        Y,         DC,     DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC),
    SLD           -> List(U_ROT,  DC,         RA_ZERO,       RB_RB,          RS_RS, ROUT_RA,   DC,     N,       DC,  DC,   CMP_RC_RC, N,      N,     DC,      DC,       N,         N,        N,         DC,     DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC),
    SLW           -> List(U_ROT,  DC,         RA_ZERO,       RB_RB,          RS_RS, ROUT_RA,   DC,     N,       DC,  DC,   CMP_RC_RC, Y,      N,     DC,      DC,       N,         N,        N,         DC,     DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC),
    SRAD          -> List(U_ROT,  DC,         RA_ZERO,       RB_RB,          RS_RS, ROUT_RA,   DC,     Y,       DC,  DC,   CMP_RC_RC, N,      Y,     DC,      DC,       Y,         N,        N,         DC,     DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC),
    SRADI         -> List(U_ROT,  DC,         RA_ZERO,       RB_CONST_SH,    RS_RS, ROUT_RA,   DC,     Y,       DC,  DC,   CMP_RC_RC, N,      Y,     DC,      DC,       Y,         N,        N,         DC,     DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC),
    SRAW          -> List(U_ROT,  DC,         RA_ZERO,       RB_RB,          RS_RS, ROUT_RA,   DC,     Y,       DC,  DC,   CMP_RC_RC, Y,      Y,     DC,      DC,       Y,         N,        N,         DC,     DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC),
    SRAWI         -> List(U_ROT,  DC,         RA_ZERO,       RB_CONST_SH32,  RS_RS, ROUT_RA,   DC,     Y,       DC,  DC,   CMP_RC_RC, Y,      Y,     DC,      DC,       Y,         N,        N,         DC,     DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC),
    SRD           -> List(U_ROT,  DC,         RA_ZERO,       RB_RB,          RS_RS, ROUT_RA,   DC,     N,       DC,  DC,   CMP_RC_RC, N,      N,     DC,      DC,       Y,         N,        N,         DC,     DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC),
    SRW           -> List(U_ROT,  DC,         RA_ZERO,       RB_RB,          RS_RS, ROUT_RA,   DC,     N,       DC,  DC,   CMP_RC_RC, Y,      N,     DC,      DC,       Y,         N,        N,         DC,     DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC),
    LBZ           -> List(U_LDST, LDST_LOAD,  RA_RA_OR_ZERO, RB_CONST_SI,    DC,    ROUT_RT,   DC,     DC,      DC,  DC,   CMP_RC_0,  DC,     N,     DC,      DC,       DC,        DC,       DC,        LEN_1B, N,          N,     N,          DC,  DC,      DC,        DC,         DC),
    LBZU          -> List(U_LDST, LDST_LOAD,  RA_RA_OR_ZERO, RB_CONST_SI,    DC,    ROUT_RT,   CA_0,   DC,      DC,  DC,   CMP_RC_0,  DC,     N,     N,       DC,       DC,        DC,       DC,        LEN_1B, N,          Y,     N,          DC,  DC,      DC,        DC,         DC),
    LHA           -> List(U_LDST, LDST_LOAD,  RA_RA_OR_ZERO, RB_CONST_SI,    DC,    ROUT_RT,   DC,     DC,      DC,  DC,   CMP_RC_0,  DC,     Y,     DC,      DC,       DC,        DC,       DC,        LEN_2B, N,          N,     N,          DC,  DC,      DC,        DC,         DC),
    LHAU          -> List(U_LDST, LDST_LOAD,  RA_RA_OR_ZERO, RB_CONST_SI,    DC,    ROUT_RT,   CA_0,   DC,      DC,  DC,   CMP_RC_0,  DC,     Y,     N,       DC,       DC,        DC,       DC,        LEN_2B, N,          Y,     N,          DC,  DC,      DC,        DC,         DC),
    LHZ           -> List(U_LDST, LDST_LOAD,  RA_RA_OR_ZERO, RB_CONST_SI,    DC,    ROUT_RT,   DC,     DC,      DC,  DC,   CMP_RC_0,  DC,     N,     DC,      DC,       DC,        DC,       DC,        LEN_2B, N,          N,     N,          DC,  DC,      DC,        DC,         DC),
    LHZU          -> List(U_LDST, LDST_LOAD,  RA_RA_OR_ZERO, RB_CONST_SI,    DC,    ROUT_RT,   CA_0,   DC,      DC,  DC,   CMP_RC_0,  DC,     N,     N,       DC,       DC,        DC,       DC,        LEN_2B, N,          Y,     N,          DC,  DC,      DC,        DC,         DC),
    LWA           -> List(U_LDST, LDST_LOAD,  RA_RA_OR_ZERO, RB_CONST_DS,    DC,    ROUT_RT,   DC,     DC,      DC,  DC,   CMP_RC_0,  DC,     Y,     DC,      DC,       DC,        DC,       DC,        LEN_4B, N,          N,     N,          DC,  DC,      DC,        DC,         DC),
    LWZ           -> List(U_LDST, LDST_LOAD,  RA_RA_OR_ZERO, RB_CONST_SI,    DC,    ROUT_RT,   DC,     DC,      DC,  DC,   CMP_RC_0,  DC,     N,     DC,      DC,       DC,        DC,       DC,        LEN_4B, N,          N,     N,          DC,  DC,      DC,        DC,         DC),
    LWZU          -> List(U_LDST, LDST_LOAD,  RA_RA_OR_ZERO, RB_CONST_SI,    DC,    ROUT_RT,   CA_0,   DC,      DC,  DC,   CMP_RC_0,  DC,     N,     N,       DC,       DC,        DC,       DC,        LEN_4B, N,          Y,     N,          DC,  DC,      DC,        DC,         DC),
    LD            -> List(U_LDST, LDST_LOAD,  RA_RA_OR_ZERO, RB_CONST_DS,    DC,    ROUT_RT,   DC,     DC,      DC,  DC,   CMP_RC_0,  DC,     N,     DC,      DC,       DC,        DC,       DC,        LEN_8B, N,          N,     N,          DC,  DC,      DC,        DC,         DC),
    LDU           -> List(U_LDST, LDST_LOAD,  RA_RA_OR_ZERO, RB_CONST_DS,    DC,    ROUT_RT,   CA_0,   DC,      DC,  DC,   CMP_RC_0,  DC,     N,     N,       DC,       DC,        DC,       DC,        LEN_8B, N,          Y,     N,          DC,  DC,      DC,        DC,         DC),
    LBARX         -> List(U_LDST, LDST_LOAD,  RA_RA_OR_ZERO, RB_RB,          DC,    ROUT_RT,   DC,     DC,      DC,  DC,   CMP_RC_0,  DC,     N,     DC,      DC,       DC,        DC,       DC,        LEN_1B, N,          N,     Y,          DC,  DC,      DC,        DC,         DC),
    LBZX          -> List(U_LDST, LDST_LOAD,  RA_RA_OR_ZERO, RB_RB,          DC,    ROUT_RT,   DC,     DC,      DC,  DC,   CMP_RC_0,  DC,     N,     DC,      DC,       DC,        DC,       DC,        LEN_1B, N,          N,     N,          DC,  DC,      DC,        DC,         DC),
    LBZUX         -> List(U_LDST, LDST_LOAD,  RA_RA_OR_ZERO, RB_RB,          DC,    ROUT_RT,   CA_0,   DC,      DC,  DC,   CMP_RC_0,  DC,     N,     N,       DC,       DC,        DC,       DC,        LEN_1B, N,          Y,     N,          DC,  DC,      DC,        DC,         DC),
    LHARX         -> List(U_LDST, LDST_LOAD,  RA_RA_OR_ZERO, RB_RB,          DC,    ROUT_RT,   DC,     DC,      DC,  DC,   CMP_RC_0,  DC,     N,     DC,      DC,       DC,        DC,       DC,        LEN_2B, N,          N,     Y,          DC,  DC,      DC,        DC,         DC),
    LHAX          -> List(U_LDST, LDST_LOAD,  RA_RA_OR_ZERO, RB_RB,          DC,    ROUT_RT,   DC,     DC,      DC,  DC,   CMP_RC_0,  DC,     Y,     DC,      DC,       DC,        DC,       DC,        LEN_2B, N,          N,     N,          DC,  DC,      DC,        DC,         DC),
    LHAUX         -> List(U_LDST, LDST_LOAD,  RA_RA_OR_ZERO, RB_RB,          DC,    ROUT_RT,   CA_0,   DC,      DC,  DC,   CMP_RC_0,  DC,     Y,     N,       DC,       DC,        DC,       DC,        LEN_2B, N,          Y,     N,          DC,  DC,      DC,        DC,         DC),
    LHBRX         -> List(U_LDST, LDST_LOAD,  RA_RA_OR_ZERO, RB_RB,          DC,    ROUT_RT,   DC,     DC,      DC,  DC,   CMP_RC_0,  DC,     N,     DC,      DC,       DC,        DC,       DC,        LEN_2B, Y,          N,     N,          DC,  DC,      DC,        DC,         DC),
    LHZX          -> List(U_LDST, LDST_LOAD,  RA_RA_OR_ZERO, RB_RB,          DC,    ROUT_RT,   DC,     DC,      DC,  DC,   CMP_RC_0,  DC,     N,     DC,      DC,       DC,        DC,       DC,        LEN_2B, N,          N,     N,          DC,  DC,      DC,        DC,         DC),
    LHZUX         -> List(U_LDST, LDST_LOAD,  RA_RA_OR_ZERO, RB_RB,          DC,    ROUT_RT,   CA_0,   DC,      DC,  DC,   CMP_RC_0,  DC,     N,     N,       DC,       DC,        DC,       DC,        LEN_2B, N,          Y,     N,          DC,  DC,      DC,        DC,         DC),
    LWARX         -> List(U_LDST, LDST_LOAD,  RA_RA_OR_ZERO, RB_RB,          DC,    ROUT_RT,   DC,     DC,      DC,  DC,   CMP_RC_0,  DC,     N,     DC,      DC,       DC,        DC,       DC,        LEN_4B, N,          N,     Y,          DC,  DC,      DC,        DC,         DC),
    LWAX          -> List(U_LDST, LDST_LOAD,  RA_RA_OR_ZERO, RB_RB,          DC,    ROUT_RT,   DC,     DC,      DC,  DC,   CMP_RC_0,  DC,     Y,     DC,      DC,       DC,        DC,       DC,        LEN_4B, N,          N,     N,          DC,  DC,      DC,        DC,         DC),
    LWAUX         -> List(U_LDST, LDST_LOAD,  RA_RA_OR_ZERO, RB_RB,          DC,    ROUT_RT,   CA_0,   DC,      DC,  DC,   CMP_RC_0,  DC,     Y,     N,       DC,       DC,        DC,       DC,        LEN_4B, N,          Y,     N,          DC,  DC,      DC,        DC,         DC),
    LWBRX         -> List(U_LDST, LDST_LOAD,  RA_RA_OR_ZERO, RB_RB,          DC,    ROUT_RT,   DC,     DC,      DC,  DC,   CMP_RC_0,  DC,     N,     DC,      DC,       DC,        DC,       DC,        LEN_4B, Y,          N,     N,          DC,  DC,      DC,        DC,         DC),
    LWZX          -> List(U_LDST, LDST_LOAD,  RA_RA_OR_ZERO, RB_RB,          DC,    ROUT_RT,   DC,     DC,      DC,  DC,   CMP_RC_0,  DC,     N,     DC,      DC,       DC,        DC,       DC,        LEN_4B, N,          N,     N,          DC,  DC,      DC,        DC,         DC),
    LWZUX         -> List(U_LDST, LDST_LOAD,  RA_RA_OR_ZERO, RB_RB,          DC,    ROUT_RT,   CA_0,   DC,      DC,  DC,   CMP_RC_0,  DC,     N,     N,       DC,       DC,        DC,       DC,        LEN_4B, N,          Y,     N,          DC,  DC,      DC,        DC,         DC),
    LDARX         -> List(U_LDST, LDST_LOAD,  RA_RA_OR_ZERO, RB_RB,          DC,    ROUT_RT,   DC,     DC,      DC,  DC,   CMP_RC_0,  DC,     N,     DC,      DC,       DC,        DC,       DC,        LEN_8B, N,          N,     Y,          DC,  DC,      DC,        DC,         DC),
    LDBRX         -> List(U_LDST, LDST_LOAD,  RA_RA_OR_ZERO, RB_RB,          DC,    ROUT_RT,   DC,     DC,      DC,  DC,   CMP_RC_0,  DC,     N,     DC,      DC,       DC,        DC,       DC,        LEN_8B, Y,          N,     N,          DC,  DC,      DC,        DC,         DC),
    LDX           -> List(U_LDST, LDST_LOAD,  RA_RA_OR_ZERO, RB_RB,          DC,    ROUT_RT,   DC,     DC,      DC,  DC,   CMP_RC_0,  DC,     N,     DC,      DC,       DC,        DC,       DC,        LEN_8B, N,          N,     N,          DC,  DC,      DC,        DC,         DC),
    LDUX          -> List(U_LDST, LDST_LOAD,  RA_RA_OR_ZERO, RB_RB,          DC,    ROUT_RT,   CA_0,   DC,      DC,  DC,   CMP_RC_0,  DC,     N,     N,       DC,       DC,        DC,       DC,        LEN_8B, N,          Y,     N,          DC,  DC,      DC,        DC,         DC),
    STB           -> List(U_LDST, LDST_STORE, RA_RA_OR_ZERO, RB_CONST_SI,    RS_RS, ROUT_NONE, DC,     DC,      DC,  DC,   CMP_RC_0,  DC,     N,     DC,      DC,       DC,        DC,       DC,        LEN_1B, N,          N,     N,          DC,  DC,      DC,        DC,         DC),
    STBU          -> List(U_LDST, LDST_STORE, RA_RA_OR_ZERO, RB_CONST_SI,    RS_RS, ROUT_NONE, CA_0,   DC,      DC,  DC,   CMP_RC_0,  DC,     N,     N,       DC,       DC,        DC,       DC,        LEN_1B, N,          Y,     N,          DC,  DC,      DC,        DC,         DC),
    STH           -> List(U_LDST, LDST_STORE, RA_RA_OR_ZERO, RB_CONST_SI,    RS_RS, ROUT_NONE, DC,     DC,      DC,  DC,   CMP_RC_0,  DC,     N,     DC,      DC,       DC,        DC,       DC,        LEN_2B, N,          N,     N,          DC,  DC,      DC,        DC,         DC),
    STHU          -> List(U_LDST, LDST_STORE, RA_RA_OR_ZERO, RB_CONST_SI,    RS_RS, ROUT_NONE, CA_0,   DC,      DC,  DC,   CMP_RC_0,  DC,     N,     N,       DC,       DC,        DC,       DC,        LEN_2B, N,          Y,     N,          DC,  DC,      DC,        DC,         DC),
    STW           -> List(U_LDST, LDST_STORE, RA_RA_OR_ZERO, RB_CONST_SI,    RS_RS, ROUT_NONE, DC,     DC,      DC,  DC,   CMP_RC_0,  DC,     N,     DC,      DC,       DC,        DC,       DC,        LEN_4B, N,          N,     N,          DC,  DC,      DC,        DC,         DC),
    STWU          -> List(U_LDST, LDST_STORE, RA_RA_OR_ZERO, RB_CONST_SI,    RS_RS, ROUT_NONE, CA_0,   DC,      DC,  DC,   CMP_RC_0,  DC,     N,     N,       DC,       DC,        DC,       DC,        LEN_4B, N,          Y,     N,          DC,  DC,      DC,        DC,         DC),
    STD           -> List(U_LDST, LDST_STORE, RA_RA_OR_ZERO, RB_CONST_DS,    RS_RS, ROUT_NONE, DC,     DC,      DC,  DC,   CMP_RC_0,  DC,     N,     DC,      DC,       DC,        DC,       DC,        LEN_8B, N,          N,     N,          DC,  DC,      DC,        DC,         DC),
    STDU          -> List(U_LDST, LDST_STORE, RA_RA_OR_ZERO, RB_CONST_DS,    RS_RS, ROUT_NONE, CA_0,   DC,      DC,  DC,   CMP_RC_0,  DC,     N,     N,       DC,       DC,        DC,       DC,        LEN_8B, N,          Y,     N,          DC,  DC,      DC,        DC,         DC),
    STBCX_DOT     -> List(U_LDST, LDST_STORE, RA_RA_OR_ZERO, RB_RB,          RS_RS, ROUT_NONE, DC,     DC,      DC,  DC,   CMP_RC_0,  DC,     N,     DC,      DC,       DC,        DC,       DC,        LEN_1B, N,          N,     Y,          DC,  DC,      DC,        DC,         DC),
    STBX          -> List(U_LDST, LDST_STORE, RA_RA_OR_ZERO, RB_RB,          RS_RS, ROUT_NONE, DC,     DC,      DC,  DC,   CMP_RC_0,  DC,     N,     DC,      DC,       DC,        DC,       DC,        LEN_1B, N,          N,     N,          DC,  DC,      DC,        DC,         DC),
    STBUX         -> List(U_LDST, LDST_STORE, RA_RA_OR_ZERO, RB_RB,          RS_RS, ROUT_NONE, CA_0,   DC,      DC,  DC,   CMP_RC_0,  DC,     N,     N,       DC,       DC,        DC,       DC,        LEN_1B, N,          Y,     N,          DC,  DC,      DC,        DC,         DC),
    STHBRX        -> List(U_LDST, LDST_STORE, RA_RA_OR_ZERO, RB_RB,          RS_RS, ROUT_NONE, DC,     DC,      DC,  DC,   CMP_RC_0,  DC,     N,     DC,      DC,       DC,        DC,       DC,        LEN_2B, Y,          N,     N,          DC,  DC,      DC,        DC,         DC),
    STHCX_DOT     -> List(U_LDST, LDST_STORE, RA_RA_OR_ZERO, RB_RB,          RS_RS, ROUT_NONE, DC,     DC,      DC,  DC,   CMP_RC_0,  DC,     N,     DC,      DC,       DC,        DC,       DC,        LEN_2B, N,          N,     Y,          DC,  DC,      DC,        DC,         DC),
    STHX          -> List(U_LDST, LDST_STORE, RA_RA_OR_ZERO, RB_RB,          RS_RS, ROUT_NONE, DC,     DC,      DC,  DC,   CMP_RC_0,  DC,     N,     DC,      DC,       DC,        DC,       DC,        LEN_2B, N,          N,     N,          DC,  DC,      DC,        DC,         DC),
    STHUX         -> List(U_LDST, LDST_STORE, RA_RA_OR_ZERO, RB_RB,          RS_RS, ROUT_NONE, CA_0,   DC,      DC,  DC,   CMP_RC_0,  DC,     N,     N,       DC,       DC,        DC,       DC,        LEN_2B, N,          Y,     N,          DC,  DC,      DC,        DC,         DC),
    STWBRX        -> List(U_LDST, LDST_STORE, RA_RA_OR_ZERO, RB_RB,          RS_RS, ROUT_NONE, DC,     DC,      DC,  DC,   CMP_RC_0,  DC,     N,     DC,      DC,       DC,        DC,       DC,        LEN_4B, Y,          N,     N,          DC,  DC,      DC,        DC,         DC),
    STWCX_DOT     -> List(U_LDST, LDST_STORE, RA_RA_OR_ZERO, RB_RB,          RS_RS, ROUT_NONE, DC,     DC,      DC,  DC,   CMP_RC_0,  DC,     N,     DC,      DC,       DC,        DC,       DC,        LEN_4B, N,          N,     Y,          DC,  DC,      DC,        DC,         DC),
    STWX          -> List(U_LDST, LDST_STORE, RA_RA_OR_ZERO, RB_RB,          RS_RS, ROUT_NONE, DC,     DC,      DC,  DC,   CMP_RC_0,  DC,     N,     DC,      DC,       DC,        DC,       DC,        LEN_4B, N,          N,     N,          DC,  DC,      DC,        DC,         DC),
    STWUX         -> List(U_LDST, LDST_STORE, RA_RA_OR_ZERO, RB_RB,          RS_RS, ROUT_NONE, CA_0,   DC,      DC,  DC,   CMP_RC_0,  DC,     N,     N,       DC,       DC,        DC,       DC,        LEN_4B, N,          Y,     N,          DC,  DC,      DC,        DC,         DC),
    STDBRX        -> List(U_LDST, LDST_STORE, RA_RA_OR_ZERO, RB_RB,          RS_RS, ROUT_NONE, DC,     DC,      DC,  DC,   CMP_RC_0,  DC,     N,     DC,      DC,       DC,        DC,       DC,        LEN_8B, Y,          N,     N,          DC,  DC,      DC,        DC,         DC),
    STDCX_DOT     -> List(U_LDST, LDST_STORE, RA_RA_OR_ZERO, RB_RB,          RS_RS, ROUT_NONE, DC,     DC,      DC,  DC,   CMP_RC_0,  DC,     N,     DC,      DC,       DC,        DC,       DC,        LEN_8B, N,          N,     Y,          DC,  DC,      DC,        DC,         DC),
    STDX          -> List(U_LDST, LDST_STORE, RA_RA_OR_ZERO, RB_RB,          RS_RS, ROUT_NONE, DC,     DC,      DC,  DC,   CMP_RC_0,  DC,     N,     DC,      DC,       DC,        DC,       DC,        LEN_8B, N,          N,     N,          DC,  DC,      DC,        DC,         DC),
    STDUX         -> List(U_LDST, LDST_STORE, RA_RA_OR_ZERO, RB_RB,          RS_RS, ROUT_NONE, CA_0,   DC,      DC,  DC,   CMP_RC_0,  DC,     N,     N,       DC,       DC,        DC,       DC,        LEN_8B, N,          Y,     N,          DC,  DC,      DC,        DC,         DC),
    MULLI         -> List(U_MUL,  DC,         RA_RA,         RB_CONST_SI,    DC,    ROUT_RT,   DC,     DC,      DC,  DC,   CMP_RC_0,  N,      Y,     DC,      DC,       DC,        DC,       DC,        DC,     DC,         DC,    DC,         N,   DC,      DC,        DC,         DC),
    MULHD         -> List(U_MUL,  DC,         RA_RA,         RB_RB,          DC,    ROUT_RT,   DC,     DC,      DC,  DC,   CMP_RC_RC, N,      Y,     DC,      DC,       DC,        DC,       DC,        DC,     DC,         DC,    DC,         Y,   DC,      DC,        DC,         DC),
    MULHDU        -> List(U_MUL,  DC,         RA_RA,         RB_RB,          DC,    ROUT_RT,   DC,     DC,      DC,  DC,   CMP_RC_RC, N,      N,     DC,      DC,       DC,        DC,       DC,        DC,     DC,         DC,    DC,         Y,   DC,      DC,        DC,         DC),
    MULHW         -> List(U_MUL,  DC,         RA_RA,         RB_RB,          DC,    ROUT_RT,   DC,     DC,      DC,  DC,   CMP_RC_RC, Y,      Y,     DC,      DC,       DC,        DC,       DC,        DC,     DC,         DC,    DC,         Y,   DC,      DC,        DC,         DC),
    MULHWU        -> List(U_MUL,  DC,         RA_RA,         RB_RB,          DC,    ROUT_RT,   DC,     DC,      DC,  DC,   CMP_RC_RC, Y,      N,     DC,      DC,       DC,        DC,       DC,        DC,     DC,         DC,    DC,         Y,   DC,      DC,        DC,         DC),
    MULLD         -> List(U_MUL,  DC,         RA_RA,         RB_RB,          DC,    ROUT_RT,   DC,     DC,      DC,  DC,   CMP_RC_RC, N,      Y,     DC,      DC,       DC,        DC,       DC,        DC,     DC,         DC,    DC,         N,   DC,      DC,        DC,         DC),
    MULLW         -> List(U_MUL,  DC,         RA_RA,         RB_RB,          DC,    ROUT_RT,   DC,     DC,      DC,  DC,   CMP_RC_RC, Y,      Y,     DC,      DC,       DC,        DC,       DC,        DC,     DC,         DC,    DC,         N,   DC,      DC,        DC,         DC),
    DIVDEU        -> List(U_DIV,  DIV_DIV,    RA_RA,         RB_RB,          DC,    ROUT_RT,   DC,     DC,      DC,  DC,   CMP_RC_RC, N,      N,     DC,      DC,       DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  Y,       DC,        DC,         DC),
    DIVWEU        -> List(U_DIV,  DIV_DIV,    RA_RA,         RB_RB,          DC,    ROUT_RT,   DC,     DC,      DC,  DC,   CMP_RC_RC, Y,      N,     DC,      DC,       DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  Y,       DC,        DC,         DC),
    DIVDE         -> List(U_DIV,  DIV_DIV,    RA_RA,         RB_RB,          DC,    ROUT_RT,   DC,     DC,      DC,  DC,   CMP_RC_RC, N,      Y,     DC,      DC,       DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  Y,       DC,        DC,         DC),
    DIVWE         -> List(U_DIV,  DIV_DIV,    RA_RA,         RB_RB,          DC,    ROUT_RT,   DC,     DC,      DC,  DC,   CMP_RC_RC, Y,      Y,     DC,      DC,       DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  Y,       DC,        DC,         DC),
    DIVDU         -> List(U_DIV,  DIV_DIV,    RA_RA,         RB_RB,          DC,    ROUT_RT,   DC,     DC,      DC,  DC,   CMP_RC_RC, N,      N,     DC,      DC,       DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  N,       DC,        DC,         DC),
    DIVWU         -> List(U_DIV,  DIV_DIV,    RA_RA,         RB_RB,          DC,    ROUT_RT,   DC,     DC,      DC,  DC,   CMP_RC_RC, Y,      N,     DC,      DC,       DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  N,       DC,        DC,         DC),
    DIVD          -> List(U_DIV,  DIV_DIV,    RA_RA,         RB_RB,          DC,    ROUT_RT,   DC,     DC,      DC,  DC,   CMP_RC_RC, N,      Y,     DC,      DC,       DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  N,       DC,        DC,         DC),
    DIVW          -> List(U_DIV,  DIV_DIV,    RA_RA,         RB_RB,          DC,    ROUT_RT,   DC,     DC,      DC,  DC,   CMP_RC_RC, Y,      Y,     DC,      DC,       DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  N,       DC,        DC,         DC),
    MODUD         -> List(U_DIV,  DIV_MOD,    RA_RA,         RB_RB,          DC,    ROUT_RT,   DC,     DC,      DC,  DC,   CMP_RC_RC, N,      N,     DC,      DC,       DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  N,       DC,        DC,         DC),
    MODUW         -> List(U_DIV,  DIV_MOD,    RA_RA,         RB_RB,          DC,    ROUT_RT,   DC,     DC,      DC,  DC,   CMP_RC_RC, Y,      N,     DC,      DC,       DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  N,       DC,        DC,         DC),
    MODSD         -> List(U_DIV,  DIV_MOD,    RA_RA,         RB_RB,          DC,    ROUT_RT,   DC,     DC,      DC,  DC,   CMP_RC_RC, N,      Y,     DC,      DC,       DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  N,       DC,        DC,         DC),
    MODSW         -> List(U_DIV,  DIV_MOD,    RA_RA,         RB_RB,          DC,    ROUT_RT,   DC,     DC,      DC,  DC,   CMP_RC_RC, Y,      Y,     DC,      DC,       DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  N,       DC,        DC,         DC),
    SYNC          -> List(U_NONE, DC,         DC,            DC,             DC,    DC,        DC,     DC,      DC,  DC,   CMP_RC_0,  DC,     DC,    DC,      DC,       DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC),
    ISYNC         -> List(U_NONE, DC,         DC,            DC,             DC,    DC,        DC,     DC,      DC,  DC,   CMP_RC_0,  DC,     DC,    DC,      DC,       DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC),
    DCBF          -> List(U_NONE, DC,         DC,            DC,             DC,    DC,        DC,     DC,      DC,  DC,   CMP_RC_0,  DC,     DC,    DC,      DC,       DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC),
    DCBST         -> List(U_NONE, DC,         DC,            DC,             DC,    DC,        DC,     DC,      DC,  DC,   CMP_RC_0,  DC,     DC,    DC,      DC,       DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC),
    DCBT          -> List(U_NONE, DC,         DC,            DC,             DC,    DC,        DC,     DC,      DC,  DC,   CMP_RC_0,  DC,     DC,    DC,      DC,       DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC),
    DCBTST        -> List(U_NONE, DC,         DC,            DC,             DC,    DC,        DC,     DC,      DC,  DC,   CMP_RC_0,  DC,     DC,    DC,      DC,       DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC),
    ICBI          -> List(U_NONE, DC,         DC,            DC,             DC,    DC,        DC,     DC,      DC,  DC,   CMP_RC_0,  DC,     DC,    DC,      DC,       DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC),
    ICBT          -> List(U_NONE, DC,         DC,            DC,             DC,    DC,        DC,     DC,      DC,  DC,   CMP_RC_0,  DC,     DC,    DC,      DC,       DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC),
    CNTLZD        -> List(U_ZER,  DC,         DC,            DC,             RS_RS, ROUT_RA,   DC,     DC,      DC,  DC,   CMP_RC_RC, N,      DC,    DC,      DC,       DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  DC,      N,         DC,         DC),
    CNTLZW        -> List(U_ZER,  DC,         DC,            DC,             RS_RS, ROUT_RA,   DC,     DC,      DC,  DC,   CMP_RC_RC, Y,      DC,    DC,      DC,       DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  DC,      N,         DC,         DC),
    CNTTZD        -> List(U_ZER,  DC,         DC,            DC,             RS_RS, ROUT_RA,   DC,     DC,      DC,  DC,   CMP_RC_RC, N,      DC,    DC,      DC,       DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  DC,      Y,         DC,         DC),
    CNTTZW        -> List(U_ZER,  DC,         DC,            DC,             RS_RS, ROUT_RA,   DC,     DC,      DC,  DC,   CMP_RC_RC, Y,      DC,    DC,      DC,       DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  DC,      Y,         DC,         DC),
    POPCNTB       -> List(U_POP,  DC,         DC,            DC,             RS_RS, ROUT_RA,   DC,     DC,      DC,  DC,   CMP_RC_0,  DC,     DC,    DC,      DC,       DC,        DC,       DC,        LEN_1B, DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC),
    POPCNTW       -> List(U_POP,  DC,         DC,            DC,             RS_RS, ROUT_RA,   DC,     DC,      DC,  DC,   CMP_RC_0,  DC,     DC,    DC,      DC,       DC,        DC,       DC,        LEN_4B, DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC),
    POPCNTD       -> List(U_POP,  DC,         DC,            DC,             RS_RS, ROUT_RA,   DC,     DC,      DC,  DC,   CMP_RC_0,  DC,     DC,    DC,      DC,       DC,        DC,       DC,        LEN_8B, DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC),
    CMPDI         -> List(U_ADD,  DC,         RA_RA,         RB_CONST_SI,    DC,    ROUT_NONE, CA_1,   DC,      DC,  Y,    CMP_CMP,   N,      Y,     Y,       DC,       DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC),
    CMPWI         -> List(U_ADD,  DC,         RA_RA,         RB_CONST_SI,    DC,    ROUT_NONE, CA_1,   DC,      DC,  Y,    CMP_CMP,   Y,      Y,     Y,       DC,       DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC),
    CMPD          -> List(U_ADD,  DC,         RA_RA,         RB_RB,          DC,    ROUT_NONE, CA_1,   DC,      DC,  Y,    CMP_CMP,   N,      Y,     Y,       DC,       DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC),
    CMPW          -> List(U_ADD,  DC,         RA_RA,         RB_RB,          DC,    ROUT_NONE, CA_1,   DC,      DC,  Y,    CMP_CMP,   Y,      Y,     Y,       DC,       DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC),
    CMPLDI        -> List(U_ADD,  DC,         RA_RA,         RB_CONST_UI,    DC,    ROUT_NONE, CA_1,   DC,      DC,  Y,    CMP_CMP,   N,      N,     Y,       DC,       DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC),
    CMPLWI        -> List(U_ADD,  DC,         RA_RA,         RB_CONST_UI,    DC,    ROUT_NONE, CA_1,   DC,      DC,  Y,    CMP_CMP,   Y,      N,     Y,       DC,       DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC),
    CMPLD         -> List(U_ADD,  DC,         RA_RA,         RB_RB,          DC,    ROUT_NONE, CA_1,   DC,      DC,  Y,    CMP_CMP,   N,      N,     Y,       DC,       DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC),
    CMPLW         -> List(U_ADD,  DC,         RA_RA,         RB_RB,          DC,    ROUT_NONE, CA_1,   DC,      DC,  Y,    CMP_CMP,   Y,      N,     Y,       DC,       DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC),
    MFSPR         -> List(U_SPR,  SPR_MF,     DC,            DC,             DC,    ROUT_RT,   DC,     DC,      DC,  DC,   CMP_RC_0,  DC,     DC,    DC,      DC,       DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC),
    MTSPR         -> List(U_SPR,  SPR_MT,     DC,            DC,             RS_RS, ROUT_NONE, DC,     DC,      DC,  DC,   CMP_RC_0,  DC,     DC,    DC,      DC,       DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC),
    MFCR          -> List(U_CR,   CR_MF,      DC,            DC,             DC,    ROUT_RT,   DC,     DC,      DC,  DC,   CMP_RC_0,  DC,     DC,    DC,      DC,       DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  DC,      DC,        FXM_FF,     DC),
    MFOCRF        -> List(U_CR,   CR_MF,      DC,            DC,             DC,    ROUT_RT,   DC,     DC,      DC,  DC,   CMP_RC_0,  DC,     DC,    DC,      DC,       DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  DC,      DC,        FXM_ONEHOT, DC),
    MTCRF         -> List(U_CR,   CR_MT,      DC,            DC,             RS_RS, ROUT_NONE, DC,     DC,      DC,  DC,   CMP_RC_0,  DC,     DC,    DC,      DC,       DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  DC,      DC,        FXM,        DC),
    MTOCRF        -> List(U_CR,   CR_MT,      DC,            DC,             RS_RS, ROUT_NONE, DC,     DC,      DC,  DC,   CMP_RC_0,  DC,     DC,    DC,      DC,       DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  DC,      DC,        FXM_ONEHOT, DC),
    B             -> List(U_BR,   BR_UNCOND,  DC,            DC,             DC,    DC,        DC,     DC,      DC,  DC,   DC,        DC,     DC,    DC,      DC,       DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  DC,      DC,        DC,         BR_TARGET_NONE),
    BC            -> List(U_BR,   BR_COND,    DC,            DC,             DC,    DC,        DC,     DC,      DC,  DC,   DC,        DC,     DC,    DC,      DC,       DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  DC,      DC,        DC,         BR_TARGET_NONE),
    BCLR          -> List(U_BR,   BR_COND,    DC,            DC,             DC,    DC,        DC,     DC,      DC,  DC,   DC,        DC,     DC,    DC,      DC,       DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  DC,      DC,        DC,         BR_TARGET_LR),
    BCCTR         -> List(U_BR,   BR_COND,    DC,            DC,             DC,    DC,        DC,     DC,      DC,  DC,   DC,        DC,     DC,    DC,      DC,       DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  DC,      DC,        DC,         BR_TARGET_CTR),
    TDI           -> List(U_NONE, DC,         DC,            DC,             DC,    DC,        DC,     DC,      DC,  DC,   DC,        DC,     DC,    DC,      DC,       DC,        DC,       DC,        DC,     DC,         DC,    DC,         DC,  DC,      DC,        DC,         DC)
  )
}

class ControlSignals extends Bundle {
  val unit        = UInt(4.W)
  val internalOp  = UInt(2.W)
  val rA          = UInt(2.W)
  val rB          = UInt(4.W)
  val rS          = UInt(1.W)
  val rOut        = UInt(2.W)
  val carryIn     = UInt(2.W)
  val carryOut    = UInt(1.W)
  val crIn        = UInt(1.W)
  val crOut       = UInt(1.W)
  val compare     = UInt(2.W)
  val is32bit     = UInt(1.W)
  val signed      = UInt(1.W)
  val invertIn    = UInt(1.W)
  val invertOut   = UInt(1.W)
  val rightShift  = UInt(1.W)
  val clearLeft   = UInt(1.W)
  val clearRight  = UInt(1.W)
  val length      = UInt(2.W)
  val byteReverse = UInt(1.W)
  val update      = UInt(1.W)
  val reservation = UInt(1.W)
  val high        = UInt(1.W)
  val extended    = UInt(1.W)
  val countRight  = UInt(1.W)
  val fxm         = UInt(2.W)
  val brTarget    = UInt(2.W)
}

class Control(val n: Int) extends Module {
  val io = IO(new Bundle {
    val insn = Input(UInt(32.W))
    val out = Output(new ControlSignals())
  })

  val ctrlSignals = ListLookup(io.insn, Control.default, Control.map)

  io.out.unit        := ctrlSignals(0)
  io.out.internalOp  := ctrlSignals(1)
  io.out.rA          := ctrlSignals(2)
  io.out.rB          := ctrlSignals(3)
  io.out.rS          := ctrlSignals(4)
  io.out.rOut        := ctrlSignals(5)
  io.out.carryIn     := ctrlSignals(6)
  io.out.carryOut    := ctrlSignals(7)
  io.out.crIn        := ctrlSignals(8)
  io.out.crOut       := ctrlSignals(9)
  io.out.compare     := ctrlSignals(10)
  io.out.is32bit     := ctrlSignals(11)
  io.out.signed      := ctrlSignals(12)
  io.out.invertIn    := ctrlSignals(13)
  io.out.invertOut   := ctrlSignals(14)
  io.out.rightShift  := ctrlSignals(15)
  io.out.clearLeft   := ctrlSignals(16)
  io.out.clearRight  := ctrlSignals(17)
  io.out.length      := ctrlSignals(18)
  io.out.byteReverse := ctrlSignals(19)
  io.out.update      := ctrlSignals(20)
  io.out.reservation := ctrlSignals(21)
  io.out.high        := ctrlSignals(22)
  io.out.extended    := ctrlSignals(23)
  io.out.countRight  := ctrlSignals(24)
  io.out.fxm         := ctrlSignals(25)
  io.out.brTarget    := ctrlSignals(26)
}

object ControlObj extends App {
  chisel3.Driver.execute(Array[String](), () => new Control(64))
}
