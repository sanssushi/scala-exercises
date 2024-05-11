
val scala3Version = "3.4.1"
val catsEffectVersion = "3.5.4"

lazy val root = project
  .in(file("."))
  .settings(
    name := "scala-exercises",
    version := "0.1.0-SNAPSHOT",

    scalaVersion := scala3Version,

    fork := true,

    scalacOptions := Seq(
      "-feature",
      "-deprecation",
      "-language:higherKinds",
      "-language:strictEquality",
      "-source:future",
      "-unchecked"),

    libraryDependencies ++= Seq(
      "org.scala-lang.modules" %% "scala-parallel-collections" % "1.0.4",

      "org.typelevel" %% "cats-effect" % catsEffectVersion,
      "org.typelevel" %% "cats-effect-testing-scalatest" % "1.5.0" % Test,
    )
  )
