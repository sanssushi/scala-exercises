package org.sanssushi.sandbox.util

object Type:

  type Match[T1, T2] <: Boolean = T1 match
    case T2 => true

  type IsTupleOf[X] = [T <: Tuple] =>> T =:= Tuple.Filter[T, [Y] =>> Match[Y, X]]


