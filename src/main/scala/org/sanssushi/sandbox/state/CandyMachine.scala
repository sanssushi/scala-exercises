package org.sanssushi.sandbox.state

import scala.compiletime.error
import scala.compiletime.ops.int.*

import State.*
import Transitions.*

case class CandyMachine(isLocked: Boolean, candies: Int, coins: Int):
  override def toString: String = s"CandyMachine(isLocked = $isLocked, candies = $candies, coins = $coins)"

object CandyMachine:

  import Input.*

  enum Input derives CanEqual:
    case Coin, Turn

  val transitions: Transitions[Input, CandyMachine, (Int, Int)] = Transitions: i =>
    modify[CandyMachine](m =>
      i match
        case Coin if m.isLocked && m.candies > 0 =>
          m.copy(isLocked = false, coins = m.coins + 1)
        case Turn if !m.isLocked && m.candies > 0 =>
          m.copy(isLocked = true, candies = m.candies - 1)
        case _ => m
    ) >> inspect(m => (m.coins, m.candies))

end CandyMachine
