# Scala 3 Bits and Pieces

## Effects

### In Essence, What are Effect Types like IO About?

**But what is a side effect in computer programs in the first place?** Every computation that isn't a pure function call
is effectful, that is, every function call that cannot be memoized or, in other words, whose result cannot
be stored in favor of calling the function again, e.g. `readChar()`.

All real-world programs are full of effects: taking user input, writing stuff into
the database, locking a shared resource, creating a timestamp, waiting for an asynchronous
computation to complete, and so on and so forth.

**By deferring the execution of an effectful computation,
effect types allow us to compose complex effects from simpler ones in a pure functional way,
just like we can compose complex pure functions from simpler ones.**

At the very core of any effect system (e.g. monix, cats-effect or zio) there's a higher kinded type like `IO[_]` that 
allows to
- to wrap and defer the execution of an effectful computation: `unit` 
- to modify the result of the computation with a pure function: `map`
- to compose effectful computations to more complex ones: `flatMap`
- and to finally run the (composed) computation: `unsafeRun`

```scala 3
class IO[A](val unsafeRun: () => A):

  def map[B](f: A => B): IO[B] = IO:
    () => f(unsafeRun())

  def flatMap[B](f: A => IO[B]): IO[B] = IO:
    () => f(unsafeRun()).unsafeRun()

object IO:

  def unit[A](a: => A): IO[A] = IO:
    () => a
```

As a result, an instance of the effect type `IO[A]` can represent an HTTP request,
accessing a locked resource, an asynchronous computation, a database transaction - you name it - which, again, can
be composed to more complex effects via `flatMap`.

Of course, the effect system that ultimately implements
the effect type and runs the effect is much richer than this implementation. 
It will provide convenience methods and predefined effects that'll make development easier. 
For example, it will allow means to run effects in parallel, cancel effects or handle errors.

In the code examples in [`org.sanssushi.sandbox.effects.Effects`](src/main/scala/org/sanssushi/sandbox/effects/Effects.scala) I use a generic effect type `F[_]`
to focus on the abstract structure and to remove the specifics of concrete effect
types as much as possible (and that way the code can run on different effect systems.)

As demonstration of the usefulness and relative simplicity of effect composition, 
I implemented a [Semaphore](https://en.wikipedia.org/wiki/Semaphore_(programming))
composed of simpler effects. The equivalent cats-effect version
of the simple effects there are referenced in the scaladoc, if available.
Some common effects and utils are put into [`org.sanssushi.sandbox.effects.F`](src/main/scala/org/sanssushi/sandbox/effects/F.scala).
A demo use case (controlled concurrent file access) can be found
in [`org.sanssushi.sandbox.effects.EffectDemo`](src/main/scala/org/sanssushi/sandbox/effects/EffectDemo.scala) and some
test cases are covered in [`org.sanssushi.sandbox.effects.Test`](src/test/scala/org/sanssushi/sandbox/effects/Test.scala).

## State

In imperative languages mutable states are often used to construct the result of a computation step by step
using statements that produce the intermediate results in one place.

While an imperative style, like "first do this, then do that, and finally combine the intermediate results like this", 
is very intuitive, the use of statements and mutable state can make the intermediate steps 
hard to understand and test. Fortunately we
don't need statements – and not even states – for this programming style. Function composition is enough.

```scala 3
extension [T, U](f: T => U) 
  def trace: T => (T, U) = t => (t, f(t))

def doThis: A => B
def doThat: B => C
def combineResults: (B, C) => D

def composition: A => D =
  doThis andThen doThat.trace andThen combineResults.tupled
```

For the composition of computations of the same kind, like `Future` or `IO`, there's of course Scala's
for-comprehension which makes use of `flatMap` and `map` behind the scenes.

```scala 3
def doThis: A => Future[B]
def doThat: B => Future[C]
def combineResults: (B, C) => D

def composition: A => Future[D] = a =>
  for
    b <- doThis(a)
    c <- doThat(b)
  yield combineResults(b, c)
```

However, there are operations that do require states. For example there are operations 
on a bank account: `deposit`, `withdraw` and `getBalance` which need to know the current balance,
may modify it and return values derived from it. 

Without side effect, without a mutable state in the background, the state needs to be passed 
to the operations as argument.

```scala 3
def lineOfCredit: Euro = Euro(3000, 0)
def initialBalance: Euro = Euro.zero

def deposit(amount: Euro): Euro => (Euro, Unit) = balance =>
  (balance + amount, ())

def withdraw(amount: Euro): Euro => (Euro, Option[Euro]) = balance =>
  if balance + lineOfCredit >= amount
  then (balance - amount, Some(amount)) else (balance, None)

def getBalance: Euro => (Euro, Euro) = balance =>
  (balance, balance)
```

We can identify a common type signature here: `Euro => (Euro, A)`, or in general `S => (S, A)`, a function that transforms
the current state into the following state and produces a value of some type `A`. 

Carrying over the state from operation to operation is tedious, though. 
Ideally we want to be able to write code like this:

```scala 3
val accountOperations: State[Euro, String] =
  for
    _ <- deposit(Euro(100, 0))
    _ <- deposit(Euro(200, 0))
    _ <- withdraw(Euro(80, 0))
    _ <- deposit(Euro(50, 0))
    _ <- unit(log("account operations completed."))
    balance <- getBalance
  yield balance.display
  
println("Latest balance: " + accountOperations.run(initialBalance))
```

In other words, we simply want to treat stateful operations as composable operations of a certain kind – just like 
async operations using `Future` or operations that may or may not return a value using `Option`.

Turns out we can make the above code work if we're able to
- identify functions `S => (S, A)` as stateful operations: `State[S, A]`
- wrap a pure value as state operation that leaves the state unchanged: `unit`
- modify the output of a stateful operation: `map`
- compose stateful operations by carrying along the state from one to another: `flatMap`
- run the (composed) state operation(s) on an initial state: `run`

```scala 3
type State[S, A] = S => (S, A)

def unit[S, A](a: A): State[S, A] = current => (current, a)

extension [S,A] (f: State[S, A])
  def map[B](g: A => B): State[S, B] = current =>
    val (next, a) = f(current)
    (next, g(a))
    
  def flatMap[B](g: A => State[S, B]): State[S, B] = current =>
    val (next, a) = f(current)
    g(a)(next)

  def run(s: S): A =
    f(s) match
      case (_, a) => a
```

A special kind of state operations are the transitions of a [Finite-state Machine](https://en.wikipedia.org/wiki/Finite-state_machine) (FSM).
Take, for example, the state transitions of a coffee maker:

```mermaid
stateDiagram
    [*] --> Ready
    Ready --> CoffeeSelected: Coffee Selection
    CoffeeSelected --> CoffeeSelected: Payment [not enough]
    CoffeeSelected --> PreparingCoffee: Payment [complete]
    CoffeeSelected --> Ready: Cancelled
    PreparingCoffee --> Ready: Completed
```

As before, a state transition can be described as a state operation, a function `S => (S, A)` or `State[S, A]`, only
that here the transitions are linked to events.
We can describe this by expanding the state operation function to `I => S => (S, A)`, or

```scala 3
type Transition[-I, S, +A] = I => State[S, A]
```

Such function fully describes the transitions of an FSM. Note, in case an event isn't accepted in a certain state,
the transition produced by this function can always be `State.unit`, that is, the neutral (or identity) transition
that's pointing back to the current state or, alternatively, a transition that points towards a dedicated error state.
With this function in place, we can map a stream of events to state transitions and run through them
successively beginning from the start state. 

For this and other examples of using the state monad take a look at [`org.sanssushi.sandbox.state.StateDemo`](org/sanssushi/sandbox/state/StateDemo.scala).
The implementation of the coffee maker's transition function can be found in
[`org.sanssushi.sandbox.state.CoffeeMaker`](src/main/scala/org/sanssushi/sandbox/state/CoffeeMaker.scala)
For a more fleshed out version of the `State` monad see [`org.sanssushi.sandbox.state.State`](src/main/scala/org/sanssushi/sandbox/state/State.scala).
