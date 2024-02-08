package org.sanssushi.sandbox.state

import State.*

object StateDemo:

  val randomSeed = 0L

  @main
  def main(): Unit =
    diceRolls()
    coinFlip()
    bankAccountOperations()
    fsmTransitions()

  def diceRolls(): Unit =

    import Dice.*

    val `2d6` = combine(DND.d6, DND.d6)
    println("\nLet's roll some dice:")
    val diceRolls = `2d6`.roll(randomSeed)
    println(diceRolls.take(20).toList)


  def coinFlip(): Unit =

    import Coin.*

    println("\nLet's flip the coin:")
    val coinFlips = coin.flip(randomSeed)
    println(coinFlips.take(20).toList)


  def bankAccountOperations(): Unit =

    import common.Euro
    import BankAccount.*

    val operations: State[Euro, String] =
      for
        _ <- deposit(Euro(100, 0))
        _ <- deposit(Euro(50, 0))
        _ <- withdraw(Euro(20, 0))
        _ <- deposit(Euro(30, 0))
        _ <- withdraw(Euro(90, 0))
        _ <- unit(log("account operations completed."))
        balance <- getBalance
      yield balance.display

    println("\nLet's run some bank account operations:")
    val finalBalance = operations.run(Euro.zero)
    println(s"Latest balance: $finalBalance")


  def fsmTransitions() : Unit =

    import Transition.*
    import common.Euro
    import CoffeeMaker.Coffee.*
    import CoffeeMaker.CMEvent.*

    val events = Seq(Selection(Espresso),
      Cancel,
      Selection(DoubleEspresso),
      Payment(Euro(1, 0)),
      Payment(Euro(1, 0)),
      Payment(Euro(0, 50)),
      PreparationComplete)

    lazy val eventStream: LazyList[CoffeeMaker.CMEvent] =
      LazyList.from(events.iterator).map: event =>
        println(s"Event: $event")
        event

    lazy val outputStream: LazyList[CoffeeMaker.CMOut] =
      CoffeeMaker.FSM.process(eventStream)(CoffeeMaker.Init).map: output =>
        println(s"Output: $output")
        output

    println("\nLet's make some coffee:")
    outputStream.toList // materialize stream
