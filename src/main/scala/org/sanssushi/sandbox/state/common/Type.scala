package org.sanssushi.sandbox.state.common

/** Some type proves */
object Type {

  /** Type match */
  type <=[T, U] <: Boolean = T match
    case U => true

  /** Type equality */
  type ==[T, U] <: Boolean = T match
    case U => U match
      case T => true

  /** Type equality predicate */
  type Eq[T] = [X] =>> X == T

  /** Type match predicate */
  type IsA[T] = [X] =>> X <= T

  /** Prove P is true for all types in tuple T */
  type ForAll[P[_] <: Boolean] = [T <: Tuple] =>> Tuple.Filter[T, P] =:= T
}
