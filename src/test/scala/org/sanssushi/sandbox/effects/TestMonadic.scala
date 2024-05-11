package org.sanssushi.sandbox.effects

import cats.effect.*
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.syntax.parallel.*
import org.sanssushi.sandbox.effects.MonadicEffects.{Reference,Signal,Resource,Semaphore}
import org.sanssushi.sandbox.effects.F.util.*
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.{DurationInt, FiniteDuration}

class TestMonadic extends AsyncFunSuite with AsyncIOSpec with Matchers:

  test("further self-evident truths"):
      IO(1).asserting(_ shouldBe 1)


  test("reference: increments are atomic"):

    val n = 100000
    val oneToN = (1 to n).toList

    for
      ref <- Reference.pure[IO, Int](0)
      incrementResults <- oneToN.parTraverse(_ => ref.updateAndGet(x => x + 1)) // concurrent increments
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

end TestMonadic

