package org.sanssushi.sandbox.effects

import cats.effect.{Async, Sync}
import cats.syntax.applicativeError.*
import cats.syntax.flatMap.*
import cats.syntax.functor.*
import cats.syntax.apply.*
import cats.{Applicative, MonadThrow}
import org.sanssushi.sandbox.effects.F.util.*

import scala.concurrent.Promise
import scala.concurrent.duration.Duration

object Effects:
  
  /** Effect 1. Atomic access to a shared mutable state.
   *
   * @see [[cats.effect.kernel.Ref]] and [[java.util.concurrent.atomic.AtomicReference]]. */
  trait Reference[F[_], S]:
    def modify[A](f: S => (S, A)): F[A]
    def update(f: S => S): F[Unit] = modify(r => (f(r), ()))
    def get: F[S] = modify(r => (r, r))
    def set(r: S): F[Unit] = modify(_ => (r, ()))
    def getAndUpdate(f: S => S): F[S] = modify(r => (f(r), r))
    def updateAndGet(f: S => S): F[S] = modify: r =>
      val update = f(r)
      (update, update)

  object Reference:

    /** Factory method. Wraps a pure value as initial value of a new [[Reference]].
     *
     * Note, creating an instance of [[Reference]] is effectful, too. */
    def pure[F[_] : Sync, S](s: S): F[Reference[F, S]] =
      Reference(Applicative[F].pure(s))

    /** Factory method. Wraps the result of an effectful computation as initial value of a new [[Reference]].
     *
     * Note, creating an instance of [[Reference]] is effectful, too. */
    def apply[F[_] : Sync, S](fs: F[S]): F[Reference[F, S]] =
      fs.map: r =>
        new Reference[F, S]:
          // implementation wraps java AtomicReference
          val internal = new java.util.concurrent.atomic.AtomicReference(r)

          final override def modify[A](f: S => (S, A)): F[A] =
            @scala.annotation.tailrec
            def loop: A =
              // get the most recently written value ("volatile" memory effect)
              val oldValue = internal.get
              val (newValue, result) = f(oldValue)
              // try again if value was changed between get and set
              if internal.compareAndSet(oldValue, newValue) then result else loop

            F.delay(loop)

  end Reference

  /** Effect 2. Await an asynchronous signal.
   *
   * @see [[cats.effect.kernel.Deferred]] and [[scala.concurrent.Promise]] / [[scala.concurrent.Future]]. */
  trait Signal[F[_], S]:

    /** Send the signal at most once.
     *
     * @return false if the signal has been sent already, else true */
    def complete(s: S): F[Boolean]

    /** Await the signal. */
    def await: F[S]

  object Signal:

    def unit[F[_] : Async]: F[Signal[F, Unit]] = Signal[F, Unit]

    /** Factory method.
     *
     * Note, creating an instance of [[Signal]] is effectful, too. */
    def apply[F[_] : Async, A]: F[Signal[F, A]] =
      // implementation based on Promise / Future
      for
        promise <- F.delay(Promise[A])
      yield new Signal[F, A]:
        override def complete(a: A): F[Boolean] = F.delay(promise.trySuccess(a))
        override val await: F[A] = F.async(promise.future)


  /** Effect 3. Resource safety (control create-use-release lifecycle of a resource).
   *
   * @see [[cats.effect.Resource]] or [[scala.util.Using.resource]] */
  trait Resource[F[_], R]:

    /** Safely use the resource of type R */
    def use[A](use: R => F[A]): F[A]

  object Resource:

    /** Factory method.
     *
     * @param create  the effect to create the resource
     * @param release the effect to release the resource */
    def apply[F[_] : MonadThrow, R](create: F[R])(release: R => F[Unit]): Resource[F, R] =
      new Resource[F, R]:
        override def use[A](use: R => F[A]): F[A] =
          create.flatMap: r =>
            use(r).onError(_ => release(r)) <* release(r)

  /** Effect 4. Locking a resource.
   *
   * @see [[https://en.wikipedia.org/wiki/Mutex Wikipedia:Mutex]]
   */
  trait Mutex[F[_], +R]:

    /** Acquire a lock and gain exclusive access to a resource of type R. */
    def lock[A](f: R => F[A]): F[A]

  /** Effect 5. Control concurrent access to a limited resource (a generalization of [[Mutex]].)
   *
   * @see [[https://en.wikipedia.org/wiki/Semaphore_(programming) Wikipedia:Semaphore]]
   */
  trait Semaphore[F[_], +R] extends Mutex[F, R]:

    /** The level of granularity at which the [[Semaphore]] controls the concurrency. */
    def maxPermits: Int

    /** Default timeout for accessing resource (incl. acquiring permits). */
    def defaultTimeout: Duration

    /** Acquire n permits and gain privileged access to a limited resource of type R.
     * @param n the number of permits to acquire
     * @param timeout timeout for acquiring permits and using resource
     * @param f use resource
     */
    def permits[A](n: Int, timeout: Duration = defaultTimeout)(f: R => F[A]): F[A]

    /** Acquire a single permit and gain access to a limited resource of type R. */
    def permit[A](f: R => F[A]): F[A] = permits(1)(f)

    /** Acquire the maximum amount of permits and gain exclusive access to a limited resource of type R. */
    override def lock[A](f: R => F[A]): F[A] = permits(maxPermits)(f)

  object Semaphore:

    import scala.collection.immutable.{HashSet, Queue}

    // uses Signal to grant / acquire a permit requests
    private final case class PermitRequest[F[_]](size: Int, signal: Signal[F, Unit]):
      val acquirePermits: F[Unit] = signal.await
      val grantPermits: F[Boolean] = signal.complete(())

    /** Representation of the semaphore state.
     *
     * @param maxPermits no of permits the semaphore will grant at most
     * @param enqueuedRequests enqueued permit requests (still waiting or cancelled / timed out)
     * @param grantedPermits no of currently granted permits
     * @param canceledRequests cancelled / timed out permit requests
     */
    private final case class State[F[_]](maxPermits: Int,
                                         enqueuedRequests: Queue[PermitRequest[F]],
                                         grantedPermits: Int,
                                         canceledRequests: Set[PermitRequest[F]]):

      def enqueue(request: PermitRequest[F]): State[F] = copy(enqueuedRequests = enqueuedRequests.enqueue(request))
      def cancel(request: PermitRequest[F]): State[F] = copy(canceledRequests = canceledRequests + request)
      def release(request: PermitRequest[F]): State[F] = copy(grantedPermits = grantedPermits - request.size)

      @scala.annotation.tailrec
      def grantPermitsMaybe: Option[(State[F], PermitRequest[F])] =

        def skipCancelled: State[F] =
          copy(enqueuedRequests = enqueuedRequests.tail, canceledRequests = canceledRequests - enqueuedRequests.head)

        def issueNext: State[F] =
          copy(enqueuedRequests = enqueuedRequests.tail, grantedPermits = grantedPermits + enqueuedRequests.head.size)

        enqueuedRequests.headOption match
          case Some(request) if canceledRequests.contains(request) => skipCancelled.grantPermitsMaybe
          case Some(request) if grantedPermits + request.size <= maxPermits => Some(issueNext, request)
          case _ => None

    private object State:

      private[Semaphore] def apply[F[_] : Sync](maxPermits: Int): F[Reference[F, State[F]]] =
        // uses Reference for thread safe state updates
        Reference.pure(State[F](maxPermits, Queue.empty, 0, HashSet.empty))

    /** Factory method. Note, creating an instance of [[Semaphore]] is effectful, too.
     * @param _maxPermits the level of granularity at which the [[Semaphore]] controls the concurrency.
     * @param _defaultTimeout default timeout for using resource (incl. waiting for access)
     * @param fr the limited resource
     */
    def apply[F[_] : Async, R](_maxPermits: Int, _defaultTimeout: Duration)(fr: F[R]): F[Semaphore[F, R]] =
      for
        resource <- fr
        state <- State(math.max(1, _maxPermits))
      yield
        new Semaphore[F, R]:

          type StateAction = PartialFunction[State[F], (State[F], F[Unit])]
          val doNothing: StateAction = state => (state, F.unit)

          def run(action: StateAction): F[Unit] = state.modify(action orElse doNothing).flatten
          def update(f: State[F] => State[F]): StateAction = state => (f(state), run(grantPermitRequestsMaybe))

          override lazy val maxPermits: Int = math.max(1, _maxPermits)
          override lazy val defaultTimeout: Duration = _defaultTimeout

          val grantPermitRequestsMaybe: StateAction = ((state: State[F]) =>
            state.grantPermitsMaybe.map:
              case (nextState, currentRequest) =>
                (nextState, currentRequest.grantPermits
                  // if grant fails the request has been "granted" by a racing cancellation / timeout before:
                  // the associated permits are released immediately
                  .ifM(run(grantPermitRequestsMaybe), releasePermitsOrCancelRequest(currentRequest)))
            ).unlift // create partial function

          def releasePermitsOrCancelRequest(permitRequest: PermitRequest[F]): F[Unit] =
            // try granting permits first to decide if they should be released or canceled
            permitRequest.grantPermits.ifM(
              run(update(_.cancel(permitRequest))), // permits were never granted: add to canceled
              run(update(_.release(permitRequest))) // permits were granted before: release
            )

          def enqueuePermitRequest(n: Int): F[PermitRequest[F]] =
            for
              signal <- Signal[F, Unit]
              permits = PermitRequest(math.max(1, math.min(n, maxPermits)), signal)
              _ <- run(update(state => state.enqueue(permits)))
            yield permits

          override def permits[A](n: Int, timeout: Duration)(f: R => F[A]): F[A] =
            // uses Resource to manage permit lifecycle:
            // create permit request - acquire permits and set timeout - release permits / cancel request)
            Resource(enqueuePermitRequest(n))(releasePermitsOrCancelRequest)
              .use(permitRequest => (permitRequest.acquirePermits >> f(resource)).timeout(timeout))

  end Semaphore

end Effects