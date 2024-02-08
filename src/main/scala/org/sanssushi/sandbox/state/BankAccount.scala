package org.sanssushi.sandbox.state

import org.sanssushi.sandbox.state.common.Euro

import scala.math.Ordered.orderingToOrdered

object BankAccount:
  
  def log(msg: String): Unit = println(msg)
  def lineOfCredit: Euro = Euro(3000, 0)
  def initialBalance: Euro = Euro.zero

  def deposit(amount: Euro): Euro => (Euro, Unit) = balance =>
    (balance + amount, ())

  def withdraw(amount: Euro): Euro => (Euro, Option[Euro]) = balance =>
    if balance + lineOfCredit >= amount
    then (balance - amount, Some(amount)) else (balance, None)

  def getBalance: Euro => (Euro, Euro) = balance =>
    (balance, balance)
