import mill._
import mill.scalalib._

object root extends ScalaModule {

  def scalaVersion = "3.3.1"

  override def ivyDeps = Agg(
    ivy"org.typelevel::cats-effect:3.4.0",
    ivy"eu.timepit::refined:0.10.1",
  )

  override def scalacOptions: T[Seq[String]] = Seq(
    "-feature",
    "-deprecation",
    "-language:higherKinds",
    "-language:strictEquality",
    "-source:future",
    "-unchecked",
  )
  
  object test extends ScalaTests with TestModule.Munit {
    override def ivyDeps = Agg(
      ivy"org.scalameta::munit::0.7.29"
    )
  }
}

