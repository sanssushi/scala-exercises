package org.sanssushi.sandbox.state

import org.sanssushi.sandbox.state.CoffeeMaker.Coffee.*
import org.sanssushi.sandbox.state.CoffeeMaker.MachineSignal.*
import org.sanssushi.sandbox.state.CoffeeMaker.S.*
import org.sanssushi.sandbox.state.CoffeeMaker.UserInput.*
import org.sanssushi.sandbox.state.State.*
import org.sanssushi.sandbox.state.Transitions.*

object CoffeeMaker:

  /** Represents pricing and payment (in Euro cents) */
  type Euro = Long

  extension (e: Euro) def render: String = s"€${e / 100},${e % 100}"

  object Euro:
    def apply(euro: Int, cent: Int): Euro = euro * 100 + cent

  /** Represents coffee selection or coffee delivered */
  enum Coffee derives CanEqual:
    case Espresso
    case DoubleEspresso
    case Americano
    case Latte
    case Cappuccino

  extension (c: Coffee)
    def price: Euro = c match
      case Espresso => Euro(1, 10)
      case DoubleEspresso => Euro(2, 10)
      case Americano => Euro(1, 40)
      case Latte => Euro(2, 10)
      case Cappuccino => Euro(2, 40)

  /** Internal signals */
  enum MachineSignal derives CanEqual:
    case CoffeeCompleted

  /** External events */
  enum UserInput derives CanEqual:
    case Selection(selection: Coffee)
    case Payment(amount: Euro)
    case Cancel, TakeCoffeeAndChange, TakeBackPayment

  /** Input type of the state machine */
  type In = UserInput | MachineSignal
  given CanEqual[In, In] = CanEqual.derived

  /** State type of the state machine */
  enum S derives CanEqual:
    case AwaitingSelection
    case Cancelled(change: Euro)
    case AwaitingPayment(coffee: Coffee, paid: Euro)
    case PreparingCoffee(coffee: Coffee, change: Euro)
    case CoffeeIsReady(coffee: Coffee, change: Euro)

  val StartState: S = AwaitingSelection

  /** Output type of the state machine */
  final case class Out(msg: String, warn: Option[String] = None, coffee: Option[Coffee] = None, change: Option[Euro] = None)

  /** State machine / state transitions.
   * Composing state changes (<code>State.modify</code>) with output creation (<code>State.inspect</code>) */
  val transitions: Transitions[In, S, Out] = Transitions: i =>
    modify[S](s => changeState(s, i)) >> inspect[S, Out](createOut)

  /** State transition based on input */
  def changeState: (S, In) => S =
    case (AwaitingSelection, Selection(s)) => AwaitingPayment(s, s.price)
    case (AwaitingPayment(coffee, paid), Payment(p)) if paid + p < coffee.price => AwaitingPayment(coffee, paid + p)
    case (AwaitingPayment(coffee, paid), Payment(p)) => PreparingCoffee(coffee, change = (paid + p) - coffee.price)
    case (AwaitingPayment(_, 0), Cancel) => AwaitingSelection
    case (AwaitingPayment(_, paid), Cancel) => Cancelled(change = paid)
    case (Cancelled(change), TakeBackPayment) => AwaitingSelection
    case (PreparingCoffee(coffee, change), CoffeeCompleted) => CoffeeIsReady(coffee, change)
    case (CoffeeIsReady(coffee, change), TakeCoffeeAndChange) => AwaitingSelection
    case (s, _: In) => s // drop other input

  /** Output based on current state */
  def createOut: S => Out =
    case AwaitingSelection => Out("Make your selection.")
    case AwaitingPayment(coffee, paid) => Out(s"Your selection: $coffee, remaining payment: ${(coffee.price - paid).render}")
    case Cancelled(change) => Out(s"Order was cancelled. Your change: ${change.render}", change = Some(change))
    case PreparingCoffee(coffee, _) => Out(s"Preparing $coffee. Please wait.")
    case CoffeeIsReady(coffee, change) => Out(s"Enjoy your $coffee. Your change: ${change.render}", coffee = Some(coffee), change = Some(change))

end CoffeeMaker
