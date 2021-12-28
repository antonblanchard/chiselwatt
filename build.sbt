// See README.md for license details.


ThisBuild / scalaVersion     := "2.12.15"
ThisBuild / version          := "3.2.0"


lazy val root = (project in file("."))
  .settings(
    name := "chiselwatt",
    libraryDependencies ++= Seq(
      "edu.berkeley.cs" %% "chisel3" % "3.5.0-RC2",
      "edu.berkeley.cs" %% "chiseltest" % "0.5.0-RC2" % "test"
    ),
    scalacOptions ++= Seq(
      "-language:reflectiveCalls",
      "-deprecation",
      "-feature",
      "-Xcheckinit"
    ),
    addCompilerPlugin("edu.berkeley.cs" % "chisel3-plugin" % "3.5.0-RC2" cross CrossVersion.full),
  )
