package org.sanssushi.sandbox.state

import org.sanssushi.sandbox.state.CoffeeMaker.Coffee.*
import org.sanssushi.sandbox.state.CoffeeMaker.In.*
import org.sanssushi.sandbox.state.CoffeeMaker.S.*
import org.sanssushi.sandbox.state.State.*
import org.sanssushi.sandbox.state.Transitions.*

object CoffeeMaker:

  /** Pricing and payment (in Euro cents) */
  type Euro = Long
  extension (e: Euro) def render: String = s"€${e / 100},${e % 100}"

  object Euro:
    def apply(euro: Int, cent: Int): Euro = euro * 100 + cent

  /** Coffee products */
  enum Coffee derives CanEqual:
    case Espresso, DoubleEspresso, Americano, Latte, Cappuccino
    def price: Euro = this match
      case Espresso => Euro(1, 10)
      case DoubleEspresso => Euro(2, 10)
      case Americano => Euro(1, 40)
      case Latte => Euro(2, 10)
      case Cappuccino => Euro(2, 30)

  /** Events */
  enum In derives CanEqual:
    case Selection(coffee: Coffee)
    case Payment(amount: Euro)
    case Cancelled
    case Completed

  /** States */
  enum S derives CanEqual:
    case Ready
    case CoffeeSelected(coffee: Coffee, paid: Euro = 0)
    case PreparingCoffee(coffee: Coffee, change: Euro = 0)

  /** Output type */
  case class Output(msg: String, coffee: Option[Coffee] = None, change: Option[Euro] = None)
  type Unchanged = Unit
  type Out = Output | Unchanged

  /** Transitions outgoing from state */
  def outgoingTransitions: S => PartialFunction[In, (S, Out)] =

    case Ready =>
      // coffee selected
      case Selection(c) => (CoffeeSelected(c), Output(s"Selected: $c, required payment: ${c.price.render}"))

    case CoffeeSelected(coffee, paid) => {
      // insufficient payment
      case Payment(p) if paid + p < coffee.price => (CoffeeSelected(coffee, paid + p),
        Output(s"Selected: $coffee, outstanding payment: ${(coffee.price - (paid + p)).render}"))
      // payment complete
      case Payment(p) => (PreparingCoffee(coffee, change = (paid + p) - coffee.price), Output(s"Preparing coffee."))
      // order cancelled
      case Cancelled => (Ready, Output(s"Make your selection.", change = Some(paid)))
    }

    case PreparingCoffee(coffee, change) =>
      // preparation complete
      case Completed => (Ready, Output(s"Make your selection.", Some(coffee), Some(change)))

  end outgoingTransitions

  /** Neutral transition (for invalid inputs) */
  val identity: S => In => (S, Out) = s => _ => (s, ())

  /** Finite state machine of the coffee maker. */
  val stateMachine: Transitions[In, S, Out] =
    Transitions: i =>
      State: s =>
        outgoingTransitions(s).applyOrElse(i, identity(s))

end CoffeeMaker
