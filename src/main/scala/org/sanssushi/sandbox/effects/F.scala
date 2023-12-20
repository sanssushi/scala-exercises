package org.sanssushi.sandbox.effects

import cats.effect.{Async, Spawn, Sync, Temporal}
import cats.syntax.flatMap.*
import cats.syntax.functor.*
import cats.syntax.monoid.*
import cats.{Applicative, ApplicativeThrow, Defer, Functor, Monad, Monoid}

import scala.annotation.tailrec
import scala.concurrent.Future
import scala.concurrent.duration.Duration


/** Lift any value or computation into F[_] plus some basic effects and utils. */
object F:

  /** Lift a pure value of type A into effect type F[_]. */
  def pure[F[_] : Applicative, A](a: A): F[A] =
    Applicative[F].pure(a)

  /** Create a F[Unit]. */
  def unit[F[_] : Applicative]: F[Unit] =
    pure(())

  /** Lift a computation of type A into effect type F[_]:
   * <li>The computation is deferred until the evaluation of F[A].
   * <li>The computation is re-evaluated with every evaluation of F[A]. */
  def delay[F[_] : Sync, A](a: => A): F[A] =
    Sync[F].delay(a)

  /** Adapt any asynchronous computation of type A and wrap it as F[A]. */
  def async[F[_] : Async, A](fa: Future[A]): F[A] =
    Async[F].fromFuture(pure(fa))

  /** Return whichever computation finished first. */
  def race[F[_] : Spawn, A, B](fa: F[A], fb: F[B]): F[Either[A, B]] =
    Spawn[F].race(fa, fb)
  
  /** Wrap a runtime exception into the effect type F[_]. */
  def error[F[_] : ApplicativeThrow, A](msg: String) : F[A] =
    ApplicativeThrow[F].raiseError(new RuntimeException(msg))

  /** Semantic blocking for a given duration. */
  def sleep[F[_] : Temporal](duration: Duration): F[Unit] =
    Temporal[F].sleep(duration)

  /** Semantic blocking forever. */
  def never[F[_] : Temporal]: F[Unit] =
    sleep(Duration.Inf)
  
  /** Create a random number between 0.0 and 1.0. */
  def random[F[_] : Sync]: F[Double] =
    delay(scala.util.Random.nextDouble())

  /** System time in ms. */
  def timestamp[F[_] : Sync]: F[Long] =
    delay(System.currentTimeMillis)

  object util:

    extension [F[_], A](fa: F[A])

      /** Map F[A] to F[Unit]. */
      def unit(using Functor[F]): F[Unit] =
        fa.map(_ => ())

      /** Repeat the computation n times and combine the results. */
      def repeat(times: Int)(using Monad[F], Monoid[A]): F[A] =

        @tailrec
        def loop(n: Int, acc: F[A]): F[A] =
          if n == 0 then acc
          else loop(n - 1, acc.flatMap(as => fa.map(a => as |+| a)))

        loop(n = math.max(0, times), F.pure(Monoid[A].empty))

      /** Error if computation takes longer than a given duration */
      def timeout(duration: Duration)(using Defer[F], Async[F]): F[A] =
        race(sleep(duration), fa).flatMap:
          case Left(_) => error[F, A]("<timeout>")
          case Right(a) => F.pure(a)

      /** Log the value of F[A] to stdout. */
      def log(using Functor[F]): F[A] =
        fa.map: a =>
          println(s"[${String.format("%1$-15s", Thread.currentThread().getName)}] $a")
          a

      /** Log the value of F[A] and the duration it took compute to stdout. */
      def measure(using Sync[F]): F[A] =
        for
          start <- timestamp
          a <- fa
          durationMs <- timestamp.map(end => end - start)
          msg = s"result: $a, duration: ${durationMs}ms"
          _ <- F.pure(msg).log
        yield a

      /** Maybe create an error instead of evaluating the value of type A.
       * The likelihood of failure is given in percent. */
      def failMaybe(likelihoodPercent: Int = 50, msg: String = "<failed>")(using Sync[F]): F[A] =
        val likelihoodOfError = math.min(math.max(0, likelihoodPercent), 100) * 0.01
        for
          rand <- random[F]
          a <- if likelihoodOfError > rand then error(msg) else fa
        yield a

  end util

end F
