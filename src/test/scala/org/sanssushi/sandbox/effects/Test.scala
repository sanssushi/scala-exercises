package org.sanssushi.sandbox.effects

import cats.effect.IO
import cats.effect.testing.minitest.IOTestSuite
import cats.syntax.parallel.*

import scala.concurrent.duration.{DurationInt, FiniteDuration}

object Test extends IOTestSuite:
  override val timeout: FiniteDuration = 10.seconds

  test("increments are atomic"):

    def incAndGet(ref: Reference[IO, Int]): IO[Int] = ref.updateAndGet(x => x + 1)
    val oneTo100k = (1 to 100000).toList

    val result = for
      state <- Reference.pure[IO, Int](0)
      // parallel increments
      rs <- oneTo100k.parTraverse(_ => incAndGet(state))
    yield rs

    // each number from 1 to 100000 should be in the result list
    result.map: increments =>
      val set = increments.toSet
      assert(set.size == 100000 && oneTo100k.forall(i => set.contains(i)))
  
end Test

