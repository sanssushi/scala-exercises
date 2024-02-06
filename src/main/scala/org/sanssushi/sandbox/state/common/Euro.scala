package org.sanssushi.sandbox.state.common

import scala.annotation.targetName

/** Currency (in Euro cents) */
opaque type Euro = Long

object Euro:

  given Ordering[Euro] with
    def compare(x: Euro, y: Euro): Int = java.lang.Long.compare(x, y)

  final val zero: Euro = 0L

  def apply(euro: Int, cent: Int): Euro = 100L * euro + cent
  def apply(cents: Long): Euro = cents

  extension (e: Euro)
    @targetName("minus")
    def -(that: Euro): Euro = e - that

    @targetName("plus")
    def +(that: Euro): Euro = e + that

    def display: String = s"€${e / 100},${e % 100}"
