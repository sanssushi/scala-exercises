package org.sanssushi.sandbox.state

import State.*
import Transitions.*

object RNG:

  type SEED = Long
  type Random[A] = State[SEED, A]

  lazy val randomInt: Random[Int] = seed1 =>
    val seed2 = (seed1 * 0x5DEECE66DL + 0xBL) & 0xFFFFFFFFFFFFL
    val n = (seed2 >>> 16).toInt
    (seed2, n)

  lazy val randomNonNegativeInt: Random[Int] = randomInt.map(i => if i < 0 then i + 1 + Int.MaxValue else i)
  lazy val diceRolls: Random[Int] = randomNonNegativeIntLessThan(6).map(x => x + 1)
  lazy val randomBoolean: Random[Boolean] = randomNonNegativeInt.map(_ % 2 == 0)

  def randomNonNegativeIntLessThan(n: Int): Random[Int] =
    randomNonNegativeInt.map(i => i % math.max(1, n))

  def rollForever(seed: SEED): LazyList[Int] =
    diceRolls.unfold(seed)

end RNG

case class CandyMachine(isLocked: Boolean, candies: Int, coins: Int):
  override def toString: String = s"CandyMachine(isLocked = $isLocked, candies = $candies, coins = $coins)"

object CandyMachine:

  import Input.*

  enum Input derives CanEqual:
    case Coin, Turn

  val transitions: Transitions[Input, CandyMachine, (Int, Int)] = i =>
    modify[CandyMachine](m =>
      i match
        case Coin if m.isLocked && m.candies > 0 =>
          m.copy(isLocked = false, coins = m.coins + 1)
        case Turn if !m.isLocked && m.candies > 0 =>
          m.copy(isLocked = true, candies = m.candies - 1)
        case _ => m
    ) followedBy inspect(m => (m.coins, m.candies))

end CandyMachine
