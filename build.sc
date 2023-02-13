// Works with mill 0.6.0
import mill._, scalalib._
import coursier.MavenRepository

/**
 * Scala 2.12 module that is source-compatible with 2.11.
 * This is due to Chisel's use of structural types. See
 * https://github.com/freechipsproject/chisel3/issues/606
 */
trait HasXsource211 extends ScalaModule {
  override def scalacOptions = T {
    super.scalacOptions() ++ Seq(
      "-deprecation",
      "-unchecked",
      "-Xsource:2.11"
    )
  }
}

trait HasChisel3 extends ScalaModule {
  override def ivyDeps = Agg(
    ivy"edu.berkeley.cs::chisel3:3.5.0"
  )
  // These lines are needed to use snapshot version of Chisel.
  def repositories = super.repositories ++ Seq(
    MavenRepository("https://oss.sonatype.org/content/repositories/snapshots")
  )
}

trait HasChiselTests extends CrossSbtModule  {
  object test extends Tests {
    override def ivyDeps = Agg(
      ivy"edu.berkeley.cs::chiseltest:0.5.0"
    )
    // These lines are needed to use snapshot version of Chisel.
    def repositories = super.repositories ++ Seq(
      MavenRepository("https://oss.sonatype.org/content/repositories/snapshots")
    )
    def testFrameworks = Seq("org.scalatest.tools.Framework")
  }
}


object chiselwatt extends CrossSbtModule with HasChisel3 with HasChiselTests with HasXsource211 {
  override def millSourcePath = super.millSourcePath
  def crossScalaVersion = "2.12.15"
  def mainClass = Some("CoreObj")
}
