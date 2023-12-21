package org.sanssushi.sandbox.effects

import cats.effect.IO
import cats.effect.testing.minitest.IOTestSuite
import cats.syntax.parallel.*

import scala.concurrent.duration.{DurationInt, FiniteDuration}

object Test extends IOTestSuite:
  override val timeout: FiniteDuration = 10.seconds

  test("increments are atomic"):

    def incAndGet(ref: Reference[IO, Int]): IO[Int] = ref.updateAndGet(x => x + 1)
    val n = 100000
    val oneToN = (1 to n).toList

    val result = for
      state <- Reference.pure[IO, Int](0)
      // concurrent increments
      rs <- oneToN.parTraverse(_ => incAndGet(state))
    yield rs

    // all numbers from 1 to n should be in the result list
    result.map: increments =>
      val set = increments.toSet
      assert(set.size == n && oneToN.forall(i => set.contains(i)))


  test("signal is sent once"):

      for
        signal <- Signal[IO,Either[Unit,Unit]]
        firstComplete <- signal.complete(Left(()))
        secondComplete <- signal.complete(Right(()))
        signalReceived <- signal.await
      yield assert(firstComplete && !secondComplete && signalReceived == Left(()))

end Test

