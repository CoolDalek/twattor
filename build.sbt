import scala.scalanative.build._

ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.2.1"

lazy val root = (project in file("."))
  .enablePlugins(ScalaNativePlugin)
  .settings(
    name := "twattor",
    nativeConfig ~= {
      _.withLTO(LTO.thin)
        .withMode(Mode.releaseFull)
        .withGC(GC.commix)
    }
  )