package org.sanssushi.sandbox.state

import scala.annotation.targetName

/** A stateful operation */
type State[S, +A] = S => (S, A)

/** State transitions based on input events */
type Transition[-I, S, +A] = I => State[S, A]

object State:

  import Transition.*

  extension[S,A](f: State[S,A])

    def map[B](g: A => B): State[S, B] = s =>
      val (s1, a1) = f(s)
      (s1, g(a1))

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

  def unit[S, A](a: A): State[S, A] = s => (s, a)
  def get[S]: State[S, S] = s => (s, s)
  def set[S](s: S): State[S, Unit] = _ => (s, ())
  def modify[S](f: S => S): State[S, Unit] = s => (f(s), ())
  def inspect[S,A](f: S => A): State[S,A] = s => (s, f(s))

  def run[S, A]: LazyList[State[S, A]] => S => LazyList[A] =
    identity[State[S, A]].process

  def sequence[S,A]: Seq[State[S, A]] => State[S, Seq[A]] =
    identity[State[S,A]].traverse

  /** TC for combining state tuples.
   * @see [[State.combine]] */
  opaque type Combiner[S, T <: Tuple, O <: Tuple] = T => State[S,O]

  // TC instances

  // base case: combine an empty tuple
  given combineEmpty[S]: Combiner[S, EmptyTuple, EmptyTuple] = _ => State.unit(EmptyTuple)

  // general case: combine a non empty tuple that starts with a State[S, HO]
  given combineNonEmpty[S, HO, TL <: Tuple, TO <: Tuple]
  (using c: Combiner[S, TL, TO]): Combiner[S, State[S, HO] *: TL, HO *: TO] = t =>
    for
      ho <- t.head
      to <- c(t.tail)
    yield ho *: to

  // fallback: create a custom compile error
  inline given typeError[S, T <: Tuple, O <: Tuple]: Combiner[S, T, O] =
    compiletime.error(
      "Tuple does not conform to (State[S, A1], State[S, A2], ..., State[S, AN]) or\n" +
      "the combined type State[S, (A1, A2, ..., AN)] does match the expected result type.")

  /** Combine a state tuple. Transforms
   * <code>(State[S, A1], State[S, A2], ..., State[S, AN])</code> to <code>State[S, (A1, A2, ..., AN)]</code> */
  def combine[S, T <: Tuple, O <: Tuple](t: T)(using c: Combiner[S, T, O]): State[S, O] = c(t)


object Transition:

  import State.*

  extension [I, S, O](transition: Transition[I, S, O])

    def process: LazyList[I] => S => LazyList[O] =
      case LazyList() => _ => LazyList.empty
      case hd #:: tl => s =>
        val (sNext, a) = transition(hd)(s)
        a #:: process(tl)(sNext)

    def traverse(input: Seq[I]): State[S, Seq[O]] =
      val start: State[S, List[O]] = State.unit(Nil)
      val combined = input.map(transition).foldLeft(start): (acc, state) =>
        for
          as <- acc
          a <- state
        yield a :: as
      combined.map(_.reverse)
