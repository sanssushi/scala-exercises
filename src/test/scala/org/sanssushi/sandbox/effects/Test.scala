package org.sanssushi.sandbox.effects

import cats.effect.IO
import cats.effect.testing.minitest.IOTestSuite
import cats.syntax.parallel.*

import F.util.*

import scala.concurrent.duration.{DurationInt, FiniteDuration}

object Test extends IOTestSuite:
  override val timeout: FiniteDuration = 10.seconds

  test("reference: increments are atomic"):

    def incAndGet(ref: Reference[IO, Int]): IO[Int] = ref.updateAndGet(x => x + 1)
    val n = 100000
    val oneToN = (1 to n).toList

    for
      state <- Reference.pure[IO, Int](0)
      incrementResults <- oneToN.parTraverse(_ => incAndGet(state)) // concurrent increments
    yield
      val set = incrementResults.toSet
      assert(oneToN.forall(i => set.contains(i)))

  test("signal: is sent once"):
    for
      signal <- Signal[IO,Either[Unit,Unit]]
      firstComplete <- signal.complete(Left(()))
      secondComplete <- signal.complete(Right(()))
      signalReceived <- signal.await
    yield assert(firstComplete && !secondComplete && signalReceived == Left(()))

  test("resource: is released on error"):
    for
      release <- Signal.unit[IO]
      resource = Resource[IO, Unit](F.unit)(_ => release.complete(()).unit)
      _ <- resource.use(_ => F.error("boom!")).recover(_ => ())
      _ <- release.await
    yield assert(true)

  test("semaphore: lock is exclusive"):

    object x:
      var value: Int = 0
      def unsafeIncrement(): Unit =
        value += 1

    val n = 100000
    val oneToN = (1 to n).toList

    for
      mutex <- Semaphore.apply[IO, Unit](100, 10.seconds)(F.unit)
      _ <- oneToN.parTraverse: _ => // parallel unsafe increments...
        mutex.lock: _ => // ...coordinated by lock
          F.delay[IO, Unit](x.unsafeIncrement())
    yield assert(x.value == n)

end Test

