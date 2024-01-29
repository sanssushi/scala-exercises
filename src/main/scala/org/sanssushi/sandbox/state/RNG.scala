package org.sanssushi.sandbox.state

import org.sanssushi.sandbox.state.RNG.{Random, SEED}
import org.sanssushi.sandbox.state.State.*

import scala.annotation.targetName

object RNG:

  type SEED = Long
  type Random[+A] = State[SEED, A]

  /** calculate next pseudo random number and seed based on current seed,
   * see https://en.wikipedia.org/wiki/Linear_congruential_generator */
  val next: Random[Int] = State: seed1 =>
    val seed2 = (seed1 * 0x5DEECE66DL + 0xBL) & 0xFFFFFFFFFFFFL
    val i = (seed2 >>> 16).toInt
    (seed2, i)

  val nextNonNegative: Random[Int] =
    next.map(i => if i < 0 then i + 1 + Int.MaxValue else i)

  val nextBoolean: Random[Boolean] =
    next.map(_ % 2 == 0)

end RNG

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
  
  object DND:

    lazy val d4: Random[DiceRoll] = Dice(4)
    lazy val d6: Random[DiceRoll] = Dice(6)
    lazy val d8: Random[DiceRoll] = Dice(8)
    lazy val d12: Random[DiceRoll] = Dice(12)
    lazy val d20: Random[DiceRoll] = Dice(20)

    /** facets 0 to 9 */
    lazy val d10: Random[DiceRoll] = Dice(10).map(_ - 1)

    /** facets 0, 10, 20, ..., 90 */
    @targetName("percentileDice")
    lazy val `d%`: Random[DiceRoll] = d10.map(_ * 10)

    lazy val `2d6`: Random[(DiceRoll, DiceRoll)] = combine(d6, d6)

  end DND

end Dice

