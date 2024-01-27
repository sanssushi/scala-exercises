package org.sanssushi.sandbox.state

import org.sanssushi.sandbox.util.Type
import scala.annotation.targetName
import scala.compiletime.summonInline

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

  /** Extract common S for state tuple. */
  type CommonS[T <: Tuple] = T match
    case State[s, ?] *: tl =>
      Tuple.Filter[T, [X] =>> Type.Match[X, State[s, Any]]] match
        case T => s

  /** Derive type (A1, A2, ..., AN) for state tuple. */
  type CombinedA[T <: Tuple] = Tuple.Map[T, [X] =>>
    X match
      case State[?, a] => a]

  extension [T <: NonEmptyTuple](t: T)
    /** combine (State[S, A1], State[S, A1], ..., State[S, AN]) to
     * State[S, (A1, A2, ..., AN)] */
    def combined[S >: CommonS[T] <: CommonS[T], A >: CombinedA[T] <: CombinedA[T]](using ev: Type.IsTupleOf[State[S, Any]][T]): State[S, A] =
      // cast to and from Seq to reuse State.sequence (with proper type checks in place)
      State.sequence(t.toList.asInstanceOf[Seq[State[S, Any]]])
        .map(Array[Any])
        .map(Tuple.fromArray)
        .map(_.asInstanceOf[A])

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


