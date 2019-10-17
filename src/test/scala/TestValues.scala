import chisel3._

object TestValues {
  val range1 : Seq[BigInt] = (0 to 63).map(c => BigInt(1) << c)
  val range2 : Seq[BigInt] = (0 to 64).map(c => (BigInt(1) << c)-1)
  val range3 : Seq[BigInt] = range2.map(c => c^ (BigInt("ffffffffffffffff", 16)))
  val range4 : Seq[BigInt] = Seq(
    BigInt("1111111111111111", 16),     /* low bits */
    BigInt("8888888888888888", 16),     /* high bits */
    BigInt("00000000FFFFFFFF", 16),     /* 32 bit all ones */
    BigInt("FFFFFFFF00000000", 16),     /* high 32 bits all ones */
    BigInt("0000000100000000", 16),     /* 2 ^ 32 */
    BigInt("00000000ffffff80", 16),     /* signed char min */
    BigInt("000000000000007f", 16),     /* signed char max */
    BigInt("00000000000000ff", 16),     /* unsigned char max */
    BigInt("00000000ffff8000", 16),     /* short min */
    BigInt("0000000000007fff", 16),     /* short max */
    BigInt("000000000000ffff", 16),     /* unsigned short max */
    BigInt("0000000080000000", 16),     /* int min */
    BigInt("000000007fffffff", 16),     /* int max */
    BigInt("00000000ffffffff", 16),     /* unsigned int max */
    BigInt("8000000000000000", 16),     /* long max */
    BigInt("7fffffffffffffff", 16),     /* long min */
    BigInt("ffffffffffffffff", 16),     /* unsigned long max */
    BigInt("0001020304050607", 16),
    BigInt("0706050403020100", 16),
    BigInt("0011223344556677", 16),
    BigInt("7766554433221100", 16),
    BigInt("0000000100020003", 16),
    BigInt("0003000200010001", 16),
    BigInt("0000111122223333", 16),
    BigInt("3333222211110000", 16),
    BigInt("00FF00FF00FF00FF", 16),
    BigInt("FF00FF00FF00FF00", 16),
    BigInt("a5a5a5a5a5a5a5a5", 16)
  )

  val range5 = Seq.fill(1000)(BigInt(64, scala.util.Random))

  val testValuesRealShort = range4
  val testValuesShort = range1 ++ range2 ++ range3 ++ range4
  val testValues = range1 ++ range2 ++ range3 ++ range4 ++ range5

  val chiselTestValues = testValues.map(c => c.asUInt)
}
