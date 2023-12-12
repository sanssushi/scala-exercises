import mill._
import mill.scalalib._

object root extends ScalaModule {

  def scalaVersion = "3.3.1"

  object test extends ScalaTests with TestModule.Munit {
    override def ivyDeps = Agg(
      ivy"org.scalameta::munit::0.7.29"
    )
  }
}

