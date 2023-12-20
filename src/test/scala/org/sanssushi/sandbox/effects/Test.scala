package org.sanssushi.sandbox.effects

import cats.effect.IO
import cats.effect.testing.minitest.IOTestSuite

import scala.concurrent.duration.{DurationInt, FiniteDuration}

object Test extends IOTestSuite:
  override val timeout: FiniteDuration = 10.seconds

  test("further self evident truths") {
    IO(assert(condition = true))
  }

