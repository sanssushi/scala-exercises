package org.sanssushi.sandbox.state

import org.sanssushi.sandbox.state.CoffeeMaker.CMEvent.*
import org.sanssushi.sandbox.state.CoffeeMaker.CMOut.*
import org.sanssushi.sandbox.state.CoffeeMaker.CMState.*
import org.sanssushi.sandbox.state.CoffeeMaker.Coffee.*
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
  enum CMEvent derives CanEqual:
    case Selection(coffee: Coffee)
    case Payment(amount: Euro)
    case Cancel
    case PreparationComplete

  /** State type */
  enum CMState derives CanEqual:
    case Ready
    case CoffeeSelected(coffee: Coffee, paid: Euro = Euro.zero)
    case PreparingCoffee(coffee: Coffee)

  /** Output type */
  enum CMOut derives CanEqual:
    case Out(msg: String, coffee: Option[Coffee] = None, change: Option[Euro] = None)
    case Unchanged

  /** Outgoing transitions grouped by state. */
  val outgoingTransitions: CMState => PartialFunction[CMEvent, (CMState, CMOut)] =
  
    case Ready => {
      // coffee selected
      case Selection(c) => (CoffeeSelected(c), Out(s"Selected: $c, required payment: ${c.price.display}"))
    }
    
    case CoffeeSelected(coffee, paid) => {
      // payment (insufficient)
      case Payment(p) if paid + p < coffee.price => (CoffeeSelected(coffee, paid + p),
        Out(s"Selected: $coffee, outstanding payment: ${(coffee.price - (paid + p)).display}"))
      // payment (complete)
      case Payment(p) =>
        val change = (paid + p) - coffee.price
        (PreparingCoffee(coffee), Out(s"Preparing coffee.", change = change.maybe))
      // order cancelled
      case Cancel =>
        (Ready, Out(s"Make your selection.", change = paid.maybe))
    }
    
    case PreparingCoffee(coffee) => {
      // preparation complete
      case PreparationComplete => (Ready, Out(s"Make your selection.", Some(coffee)))
    }

  /** Initial state of the coffee maker. */
  val Init: CMState = Ready
  /** Fallback transition pointing back to the same state with unchanged output. */
  val Identity: State[CMState, CMOut] = unit(Unchanged)
  /** Finite-state machine of the coffee maker. */
  val FSM: Transition[CMEvent, CMState, CMOut] = e => s =>
    outgoingTransitions(s).orElse(_ => Identity(s))(e)
