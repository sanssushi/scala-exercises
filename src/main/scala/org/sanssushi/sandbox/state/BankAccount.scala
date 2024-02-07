package org.sanssushi.sandbox.state

import org.sanssushi.sandbox.state.State.*
import org.sanssushi.sandbox.state.common.Euro

import scala.math.Ordered.orderingToOrdered

object BankAccount {
  
  def log(msg: String): Unit = ()
  def lineOfCredit: Euro = Euro(3000, 0)
  def initialBalance: Euro = Euro.zero

  def deposit(amount: Euro): Euro => (Euro, Unit) = balance =>
    (balance + amount, ())

  def withdraw(amount: Euro): Euro => (Euro, Option[Euro]) = balance =>
    if balance + lineOfCredit >= amount
    then (balance - amount, Some(amount)) else (balance, None)

  def getBalance: Euro => (Euro, Euro) = balance =>
    (balance, balance)
  
  val accountOperations: State[Euro, String] =
    for
      _ <- deposit(Euro(100, 0))
      _ <- deposit(Euro(50, 0))
      _ <- withdraw(Euro(20, 0))
      _ <- deposit(Euro(30, 0))
      _ <- withdraw(Euro(90, 0))
      _ <- unit(log("account operations completed."))
      balance <- getBalance
    yield balance.display
}
