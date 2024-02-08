package org.sanssushi.sandbox.state

import org.sanssushi.sandbox.state.RNG.{Random, SEED}
import org.sanssushi.sandbox.state.State.*
import org.sanssushi.sandbox.state.common.Type.*

import scala.annotation.targetName

/** The Random Number Generator is a canonical example for stateful operations: a pseudo-random value is
 * derived from an internal seed value. The seed changes consecutively with every random value generated, thus,
 * becoming the seed for subsequent random values. */
object RNG:

  type SEED = Long
  type Random[+A] = State[SEED, A]

  /** calculate next pseudo random number and seed based on current seed,
   * see https://en.wikipedia.org/wiki/Linear_congruential_generator */
  val next: Random[Int] = seed1 =>
    val seed2 = (seed1 * 0x5DEECE66DL + 0xBL) & 0xFFFFFFFFFFFFL
    val i = (seed2 >>> 16).toInt
    (seed2, i)

  val nextNonNegative: Random[Int] =
    next.map(i => if i < 0 then i + 1 + Int.MaxValue else i)

  val nextBoolean: Random[Boolean] =
    next.map(_ % 2 == 0)

end RNG

/** Coin flip derived from RNG */
object Coin:

  enum CoinFlip:
    case Heads, Tails

  val coin: Random[CoinFlip] =
    RNG.nextBoolean.map: bool =>
      if bool then CoinFlip.Heads else CoinFlip.Tails

  extension (coin: Random[CoinFlip])
    def flip(seed: SEED): LazyList[CoinFlip] =
      coin.unfold(seed)

end Coin

/** Dice roll derived from RNG */
object Dice:

  val dice: Random[DiceRoll] = Dice(6)

  type Facets = 4 | 6 | 8 | 10 | 12 | 14 | 16 | 18 | 20 | 24 | 30 | 48 | 50 | 60 | 100
  opaque type DiceRoll <: Int = Int

  /** Factory method. Create dice with facets 1 to n. */
  def apply[N <: Facets](n: N): Random[DiceRoll] =
    RNG.nextNonNegative.map(_ % n + 1)

  extension (dice: Random[DiceRoll])
    def roll(seed: SEED): LazyList[DiceRoll] =
      dice.unfold(seed)

  extension [T <: Tuple : ForAll[Eq[DiceRoll]]](dice: Random[T])
    def roll(seed: SEED): LazyList[T] =
      dice.unfold(seed)
  
  /** Your favorite dice set */
  object DND:
    val d4: Random[DiceRoll] = Dice(4)
    val d6: Random[DiceRoll] = Dice(6)
    val d8: Random[DiceRoll] = Dice(8)
    // facets 0 to 9
    val d10: Random[DiceRoll] = Dice(10).map(_ - 1)
    val d12: Random[DiceRoll] = Dice(12)
    val d20: Random[DiceRoll] = Dice(20)
    // facets 00, 10, 20, ..., 90
    @targetName("percentileDice")
    val `d%`: Random[DiceRoll] = d10.map(_ * 10)
    val d100: Random[(DiceRoll, DiceRoll)] = combine(`d%`, d10)
  end DND

end Dice

