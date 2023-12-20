
val scala3Version = "3.3.1"

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
      "org.typelevel" %% "cats-effect" % "3.5.2",
      "org.typelevel" %% "cats-effect-testing-minitest" % "1.5.0" % Test,
    )
  )
