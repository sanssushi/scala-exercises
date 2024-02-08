package org.sanssushi.sandbox.state

import org.sanssushi.sandbox.state.CoffeeMaker.Coffee.*
import org.sanssushi.sandbox.state.CoffeeMaker.Event.*
import org.sanssushi.sandbox.state.CoffeeMaker.Output.*
import org.sanssushi.sandbox.state.CoffeeMaker.Status.*
import org.sanssushi.sandbox.state.State.*
import org.sanssushi.sandbox.state.common.Euro

import scala.math.Ordered.orderingToOrdered

/** The finite-state machine of a coffee maker implemented with the state monad. */
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
  enum Event derives CanEqual:
    case Selection(coffee: Coffee)
    case Payment(amount: Euro)
    case Cancelled
    case Completed

  /** State type */
  enum Status derives CanEqual:
    case Ready
    case CoffeeSelected(coffee: Coffee, paid: Euro = Euro.zero)
    case PreparingCoffee(coffee: Coffee, change: Euro = Euro.zero)

  /** Output type */
  enum Output:
    case Out(msg: String, coffee: Option[Coffee] = None, change: Option[Euro] = None)
    case Unchanged

  val startState: Status = Ready
  val reset: State[Status, Output] = set(startState) >> unit(Out("Make your selection."))
  val identity: State[Status, Output] = unit(Unchanged)

  /** Finite-state machine of the coffee maker. */
  val fsm: Transition[Event, Status, Output] = i => s =>
    if outgoingTransitions(s).isDefinedAt(i)
    then outgoingTransitions(s)(i)
    else identity(s)

  /** Outgoing transitions grouped by state S */
  lazy val outgoingTransitions: Status => PartialFunction[Event, (Status, Output)] =

    case Ready =>
      // coffee selected
      case Selection(c) => (CoffeeSelected(c), Out(s"Selected: $c, required payment: ${c.price.display}"))

    case CoffeeSelected(coffee, paid) => {
      // insufficient payment
      case Payment(p) if paid + p < coffee.price => (CoffeeSelected(coffee, paid + p),
        Out(s"Selected: $coffee, outstanding payment: ${(coffee.price - (paid + p)).display}"))
      // payment complete
      case Payment(p) => (PreparingCoffee(coffee, change = (paid + p) - coffee.price), Out(s"Preparing coffee."))
      // order cancelled
      case Cancelled => (Ready, Out(s"Make your selection.", change = Some(paid)))
    }

    case PreparingCoffee(coffee, change) =>
      // preparation complete
      case Completed => (Ready, Out(s"Make your selection.", Some(coffee), Some(change)))
