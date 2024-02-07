package org.sanssushi.sandbox.effects

import cats.effect.{Async, IO, IOApp}
import cats.syntax.applicativeError.*
import cats.syntax.apply.*
import cats.syntax.flatMap.*
import cats.syntax.functor.*
import cats.syntax.parallel.*
import cats.{Defer, Parallel}
import org.sanssushi.sandbox.effects.Effects.*
import org.sanssushi.sandbox.effects.F.util.*

import scala.concurrent.duration.*
import scala.util.Random

object EffectDemo extends IOApp.Simple:

  // at the outermost boundary of the program we pinpoint F[_] to
  // cats.effect.IO (could as well be ZIO or Monix Task)
  override def run: IO[Unit] =
    semaphoreDemo[IO]

  /** Simple file resource.
   *
   * Can be closed. readChar takes 200ms and fails with a likelihood of 3% */
  trait File[F[_]]:
    def readChar: F[Char]
    def close: F[Unit]

  object File:
    def open[F[_] : Async](path: String): F[File[F]] =
      Reference.pure[F, Boolean](true).map: open =>
        new File[F]:
          override def readChar: F[Char] = open.get.flatMap: isOpen =>
            if isOpen
            then F.sleep(200.millis) *> F.delay(Random.between(32, 127)).map(_.toChar).failMaybe(3, "<io error>")
            else F.error[F, Char]("<file closed>")
          override def close: F[Unit] = open.set(false)

  /** Semaphore demo:
   * control the concurrent access to a file resource */
  private def semaphoreDemo[F[_] : Defer : Async : Parallel]: F[Unit] =

    /** Read some characters from file. */
    def task(id: Int, fileAccess: (F[Char] => F[String]) => F[String]): F[String] =
      F.pure(s"[task $id] waiting for file access...").log *>
        fileAccess(readChar => // blocks here until file access has been acquired
          for
            _ <- F.pure(s"[task $id] access granted").log
            _ <- F.pure(s"[task $id] reading...").log
            n <- F.delay(Random.between(1, 11))
            result <- readChar.map(_.toString).repeat(n)
            _ <- F.pure(s"[task $id] done, result: \"$result\"").log
          yield result
        ).onError(t => F.pure(s"[task $id] failed (${t.getMessage}).").log.unit)


    /** Create some tasks and control concurrency:
     * <li>20 tasks
     * <li>20 permits
     * <li>id of each task (1-20) equals the number of requested / assigned permits for that task
     * <li>timeout for tasks (incl. acquiring permits) is 10 seconds
     */
    def controlConcurrentTasks(path: String): F[List[(Int, String)]] =

      val numberOfTasks = 20
      val numberOfPermits = 20
      val timeout = 10.seconds

      // control file lifecycle via Resource
      Resource(File.open(path))(_.close >> F.pure("file closed.").log.unit).use: file =>
        // create a semaphore
        Semaphore(numberOfPermits, timeout)(F.pure(file)).flatMap: semaphore =>
          // create some tasks using default timeout
          (1 to numberOfTasks).toList.parTraverse: id =>
            val permits = id
            // acquire permits for task
            task(id, callback => semaphore.permits(permits)(file => callback(file.readChar)))
              .map(c => (id, s"\"$c\""))
              .recover(t => (id, t.getMessage))

    controlConcurrentTasks("//my/file").measure.unit

  end semaphoreDemo

end EffectDemo