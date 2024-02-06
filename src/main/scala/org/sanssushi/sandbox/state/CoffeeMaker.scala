package org.sanssushi.sandbox.state

import org.sanssushi.sandbox.state.CoffeeMaker.Coffee.*
import org.sanssushi.sandbox.state.CoffeeMaker.In.*
import org.sanssushi.sandbox.state.CoffeeMaker.S.*
import org.sanssushi.sandbox.state.State.*
import org.sanssushi.sandbox.state.Transitions.*

import org.sanssushi.sandbox.state.common.Euro

import math.Ordered.orderingToOrdered

object CoffeeMaker:
  
  /** Coffee products */
  enum Coffee derives CanEqual:
    case Espresso, DoubleEspresso, Americano, Latte, Cappuccino

    def price: Euro = this match
      case Espresso => Euro(1, 10)
      case DoubleEspresso => Euro(2, 10)
      case Americano => Euro(1, 40)
      case Latte => Euro(2, 10)
      case Cappuccino => Euro(2, 30)

  /** Event type */
  enum In derives CanEqual:
    case Selection(coffee: Coffee)
    case Payment(amount: Euro)
    case Cancelled
    case Completed

  /** State type */
  enum S derives CanEqual:
    case Ready
    case CoffeeSelected(coffee: Coffee, paid: Euro = Euro.zero)
    case PreparingCoffee(coffee: Coffee, change: Euro = Euro.zero)

  /** Output type */
  type Out = Output | Unchanged
  case class Output(msg: String, coffee: Option[Coffee] = None, change: Option[Euro] = None)
  type Unchanged = Unit

  /** Neutral transition (for invalid inputs) */
  val identity: S => In => (S, Out) = s => _ => (s, ())

  /** Finite state machine of the coffee maker. */
  val stateMachine: Transitions[In, S, Out] =
    Transitions: event =>
      State: s =>
        outgoingTransitions(s).applyOrElse(event, identity(s))

  /** Outgoing transitions grouped by state S */
  def outgoingTransitions: S => PartialFunction[In, (S, Out)] =

    case Ready =>
      // coffee selected
      case Selection(c) => (CoffeeSelected(c), Output(s"Selected: $c, required payment: ${c.price.display}"))

    case CoffeeSelected(coffee, paid) => {
      // insufficient payment
      case Payment(p) if paid + p < coffee.price => (CoffeeSelected(coffee, paid + p),
        Output(s"Selected: $coffee, outstanding payment: ${(coffee.price - (paid + p)).display}"))
      // payment complete
      case Payment(p) => (PreparingCoffee(coffee, change = (paid + p) - coffee.price), Output(s"Preparing coffee."))
      // order cancelled
      case Cancelled => (Ready, Output(s"Make your selection.", change = Some(paid)))
    }

    case PreparingCoffee(coffee, change) =>
      // preparation complete
      case Completed => (Ready, Output(s"Make your selection.", Some(coffee), Some(change)))

end CoffeeMaker