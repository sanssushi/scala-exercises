package org.sanssushi.sandbox.state

import scala.annotation.targetName

opaque type State[S, +A] = S => (S, A)
opaque type Transitions[-I, S, +A] = I => State[S, A]

object State:

  import Transitions.*

  extension[S,A](state: State[S,A])

    def map[B](f: A => B): State[S, B] = s =>
      val (s1, a1) = state(s)
      (s1, f(a1))

    def flatMap[B](f: A => State[S, B]): State[S, B] = s =>
      val (s1, a1) = state(s)
      f(a1)(s1)

    @targetName("followedBy")
    def >>[B](that: State[S, B]): State[S, B] =
      state.flatMap(_ => that)

    def unfold(s: S): LazyList[A] =
      val (nextS, a) = state(s)
      a #:: unfold(nextS)

    def run(s: S): A =
      state(s)._2

  end extension

  def apply[S, A](f: S => (S, A)): State[S, A] = f

  def unit[S, A](a: A): State[S, A] = s => (s, a)
  def get[S]: State[S, S] = s => (s, s)
  def set[S](s: S): State[S, Unit] = _ => (s, ())
  def modify[S](f: S => S): State[S, Unit] = s => (f(s), ())
  def inspect[S,A](f: S => A): State[S,A] = s => (s, f(s))

  def run[S, A]: Seq[State[S, A]] => S => LazyList[A] =
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

end State

object Transitions:

  import State.*

  def apply[I, S, A](f: I => State[S, A]): Transitions[I, S, A] = f
  
  extension [I, S, A](transitions: Transitions[I, S, A])

    def process: Seq[I] => S => LazyList[A] =
      case Nil => _ => LazyList.empty
      case hd :: tl => s =>
        val (sNext, a) = transitions(hd)(s)
        a #:: process(tl)(sNext)

    def traverse(input: Seq[I]): State[S, Seq[A]] =
      val start: State[S, List[A]] = unit(Nil)
      val combined = input.map(transitions).foldLeft(start): (acc, state) =>
          for
            as <- acc
            a <- state
          yield a :: as
      combined.map(_.reverse)

  end extension

end Transitions


