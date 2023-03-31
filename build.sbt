import scala.scalanative.build._

ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.2.2"

lazy val root = (project in file("."))
  .enablePlugins(ScalaNativePlugin)
  .settings(
    name := "twattor",
    nativeConfig ~= {
      _.withLTO(LTO.thin)
        .withMode(Mode.releaseFull)
        .withGC(GC.commix)
    },
    scalacOptions ++= Seq(
      "-explain",
      "-explain-types",
      "-deprecation",
      "-feature",
      "-source:future",
    ),
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core" % "2.9.0",
      "org.typelevel" %% "cats-kernel" % "2.9.0",
      "org.typelevel" %% "cats-free" % "2.9.0",
      "org.typelevel" %% "cats-effect" % "3.4.8",
    )
  )