package org.sanssushi.demo.effects

import cats.effect.{Async, IO, IOApp, Temporal}
import cats.syntax.applicativeError.*
import cats.syntax.apply.*
import cats.syntax.flatMap.*
import cats.syntax.functor.*
import cats.syntax.parallel.*
import cats.{Applicative, Defer, Parallel}
import org.sanssushi.demo.effects.F.util.*

import scala.concurrent.duration.*
import scala.util.Random

/**
 * In essence, what are effect types like IO[_] about?
 *
 * But first, what is an effect in computer programs?
 *
 * Every computation that isn't a pure function call is effectful, that is, every function that cannot
 * be memoized or, in other words, who's result cannot be stored and used later instead of calling
 * the function again, e.g. readChar().
 *
 * All real world programs are full of effects: taking user input, writing stuff into
 * the database, locking a shared resource, creating a timestamp, waiting for an asynchronous
 * computation to complete and so on and so forth.
 *
 * By deferring the execution of an effectful computation and wrapping it in a monadic structure,
 * effect types allow us to compose complex effects from simpler ones in a pure functional manner,
 * just like we can compose complex pure functions from simple ones.
 *
 * As a result IO[A] can represent a http request, locking a resource, an asynchronous computation,
 * a database transaction, you name it.
 *
 * And of course, the effect system (monix, cats-effect, zio, ...) that ultimately implements
 * the effect type (e.g. IO[_]) and runs the effect will provide many convenience methods and
 * predefined effects that'll make development easier.
 *
 * As an example for effect composition we're composing a
 * [[https://en.wikipedia.org/wiki/Semaphore_(programming) Wikipedia:Semaphore]] from simpler effects.
 *
 * You find the cats-effect version of the effects written here referenced in the scaladoc if available.
 *
 * A generic effect type F[_] allows us to focus on the abstract structure and remove the specifics
 * of concrete effect types as much as possible.
 *
 * The effect composition is shown in Effects.scala. Some common effects and utils are put in F.scala.
 *
 * For error handling we directly use .onError() and .recover() on the effects, they are provided by
 * [[cats.ApplicativeThrow]] / [[cats.MonadThrow]]. For parallel traversal [[cats.Parallel]]. (Those are effects, too,
 * but reside in the cats library.)
 *
 */
object Main extends IOApp.Simple:

  /** Simple file resource.
   *
   * Can be closed. readChar takes 200ms and fails with a likelihood of 2% */
  trait File[F[_]]:
    def readChar: F[Char]
    def close: F[Unit]

  object File:
    def open[F[_] : Defer : Applicative : Temporal](path: String): F[File[F]] =
      Reference.pure[F, Boolean](true).map: open =>
        new File[F]:
          override def readChar: F[Char] = open.get.flatMap: isOpen =>
            if isOpen
            then F.sleep(200.millis) *> F.delay(Random.between(32, 127)).map(_.toChar).failMaybe(2, "<io error>")
            else F.error[F, Char]("<file closed>")
          override def close: F[Unit] = open.set(false)

  /** Semaphore demo:
   * control the concurrent access to a file resource */
  def semaphoreDemo[F[_] : Defer : Async : Parallel]: F[Unit] =

    /** read between 0 and 10 characters
     * (resource lifetime, access control, timeout, all that is handled outside) */
    def task(id: Int, fileAccess: (F[Char] => F[String]) => F[String]): F[String] =
      F.pure(s"[task $id] waiting for file access...").log *>
        fileAccess(readChar => // blocks here until permits are acquired
          for
            _ <- F.pure(s"[task $id] access granted").log
            _ <- F.pure(s"[task $id] reading...").log
            n <- F.delay(Random.between(0, 10))
            result <- readChar.map(_.toString).repeat(n)
            _ <- F.pure(s"[task $id] done, result: \"$result\"").log
          yield result
        ).onError(_ => F.pure(s"[task $id] failed.").log.unit)

    /** Create some tasks and control concurrency:
     * <li>20 tasks
     * <li>20 permits
     * <li>id of each task (1-20) equals the number of requested / assigned permits for that task
     * <li>timeout for tasks (incl. acquiring permits) is 8 seconds
     */
    def controlConcurrentTasks(path: String): F[List[(Int, String)]] =
      val numberOfTasks = 20
      // control file lifecycle via Resource
      Resource(File.open(path))(_.close).use(file =>
        for
          semaphore <- Semaphore(numberOfTasks)(F.pure(file))
          result <- (1 to numberOfTasks).toList.parTraverse(id =>
            val permits = id
            // create task and control permits via Semaphore
            task(id, callback => semaphore.permits(permits)(file => callback(file.readChar)))
              .timeout(8.seconds) // set timeout
              .map(c => (id, s"\"$c\"")) // collect result
              .recover(t => (id, t.getMessage)) // recover from error
          )
        yield result
      )

    controlConcurrentTasks("//my/file").measure.unit

  end semaphoreDemo

  // at the outermost boundary of the program we pinpoint F[_] to cats.effect.IO (could as well be ZIO or Monix Task)
  override def run: IO[Unit] = semaphoreDemo[IO]

end Main