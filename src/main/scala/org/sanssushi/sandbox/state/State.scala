package org.sanssushi.sandbox.state

type State[S,+A] = S => (S, A)
type Transitions[-I,S,+A] = I => State[S,A]

object State:

  import Transitions.traverse

  extension[S,A](state: State[S,A])

    def map[B](f: A => B): State[S, B] = s =>
      val (s1, a1) = state(s)
      (s1, f(a1))

    def flatMap[B](f: A => State[S, B]): State[S, B] = s =>
      val (s1, a1) = state(s)
      f(a1)(s1)

    infix def followedBy[B](that: State[S, B]): State[S, B] =
      state.flatMap(_ => that)

    infix def after[B](that: State[S, B]): State[S, A] =
      that followedBy state

    def combine[B](that: State[S, B]): State[S, (A, B)] =
      state.flatMap(a => that.map(b => (a,b)))

    def unfold(s: S): LazyList[A] =
      val (nextS, a) = state(s)
      a #:: unfold(nextS)

    def run(s: S): A =
      state(s)._2

  end extension

  def unit[S, A](a: A): State[S, A] = s => (s, a)
  def get[S]: State[S, S] = s => (s, s)
  def set[S](s: S): State[S, Unit] = _ => (s, ())
  def modify[S](f: S => S): State[S, Unit] = s => (f(s), ())
  def inspect[S,A](f: S => A): State[S,A] = s => (s, f(s))

  def sequence[S,A](states: Seq[State[S, A]]): State[S, Seq[A]] =
    identity[State[S,A]].traverse(states)

end State

object Transitions:

  import State.*

  extension [I, S, A](transitions: Transitions[I, S, A])

    def process: (S, Seq[I]) => LazyList[A] =
      case (_, Nil) => LazyList.empty
      case (s, i :: is) =>
        val (sNext, a) = transitions(i)(s)
        a #:: process(sNext, is)

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


