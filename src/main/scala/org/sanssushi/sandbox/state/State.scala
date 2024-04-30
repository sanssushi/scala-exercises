package org.sanssushi.sandbox.state

import scala.annotation.{implicitNotFound, targetName}

/** A stateful operation */
type State[S, +A] = S => (S, A)

/** Stateful operations based on input events (e.g. the transitions of a finite-state machine) */
type Transition[-I, S, +A] = I => State[S, A]

object State:

  import Transition.*

  extension[S,A](f: State[S,A])

    /** Modify the output of the stateful operation f. */
    def map[B](g: A => B): State[S, B] = s =>
      val (s1, a1) = f(s)
      (s1, g(a1))

    /** Compose two stateful operations f and g. */
    def flatMap[B](g: A => State[S, B]): State[S, B] = s =>
      val (s1, a1) = f(s)
      g(a1)(s1)

    /** Compose two stateful operations (ignoring output of the first operation) */
    @targetName("followedBy")
    def >>[B](that: State[S, B]): State[S, B] =
      f.flatMap(_ => that)

    /** Repeat the same stateful operation indefinitely producing an infinite list of output values */
    def unfold(s: S): LazyList[A] =
      val (nextS, a) = f(s)
      a #:: unfold(nextS)

    /** Run the state operation on the initial state s */
    def run(s: S): A =
      f(s) match
        case (_, a) => a

  /** A neutral state operation with a pure value as output. */
  def unit[S, A](a: A): State[S, A] = s => (s, a)
  /** A neutral state operation with the current state as output. */
  def get[S]: State[S, S] = s => (s, s)
  /** The state operation that sets the next state regardless of the current state (no output). */
  def set[S](s: S): State[S, Unit] = _ => (s, ())
  /** The state operation that updates the state based on the current state (no output). */
  def modify[S](f: S => S): State[S, Unit] = s => (f(s), ())
  /** A neutral state operation that maps the current state to some output. */
  def inspect[S,A](f: S => A): State[S,A] = s => (s, f(s))

  /** Turn a stream of state operation into a stream of output values. */
  def run[S, A]: LazyList[State[S, A]] => S => LazyList[A] =
    identity[State[S, A]].process

  /** Compose a sequence of state operations into a single state operation with the sequence of outputs
   * as output type. */
  def sequence[S,A]: Seq[State[S, A]] => State[S, Seq[A]] =
    identity[State[S,A]].traverse

  /** TC for combining a tuple of state operations.
   * @see [[State.combine]] */
  opaque type Combiner[S, T <: Tuple, O <: Tuple] = T => State[S,O]

  /** Base case: Combiner instance for the empty tuple
   * @see [[State.Combiner]] */
  given combineEmpty[S]: Combiner[S, EmptyTuple, EmptyTuple] = State.unit

  /** General case: Combiner <code>State[S, HO] *: TL => State[S, HO *: TO]</code> for non empty tuples
   * @param c using a combiner for the tail
   * @tparam S the common type S for all tuple elements
   * @tparam HO the output type of the head element
   * @tparam TL the type of the tail elements
   * @tparam TO the output type of the combined tail elements
   * @see [[State.Combiner]] */
  given combineNonEmpty[S, HO, TL <: Tuple, TO <: Tuple]
  (using c : Combiner[S, TL, TO]): Combiner[S, State[S, HO] *: TL, HO *: TO] = t =>
    for
      ho <- t.head
      to <- c(t.tail)
    yield ho *: to

  /** Combine a tuple of state operations. Transforms
   * <code>(State[S, A1], State[S, A2], ..., State[S, AN])</code> to <code>State[S, (A1, A2, ..., AN)]</code> */
  def combine[S, T <: Tuple, O <: Tuple](t: T)(using @implicitNotFound(
          "Tuple does not conform to (State[S, A1], State[S, A2], ..., State[S, AN]) or\n" +
          "the combined type State[S, (A1, A2, ..., AN)] does match the expected result type"
  ) c: Combiner[S, T, O]): State[S, O] = c(t)


object Transition:

  import State.*

  extension [I, S, O](f: Transition[I, S, O])

    /** Run through state transitions as defined by the input event stream and the transition function f. */
    def process: LazyList[I] => S => LazyList[O] =
      case LazyList() => _ => LazyList.empty
      case hd #:: tl => s =>
        val (sNext, a) = f(hd)(s)
        a #:: process(tl)(sNext)

    /** Traverse a sequence of events and return the composition of corresponding state transitions as
     * defined by the transition function f. */
    def traverse(input: Seq[I]): State[S, Seq[O]] =
      val start: State[S, List[O]] = State.unit(Nil)
      val combined = input.map(f).foldLeft(start): (acc, state) =>
        for
          as <- acc
          a <- state
        yield a :: as
      combined.map(_.reverse)
