package com.scalamandra.concurrent

import scala.concurrent.{CancellationException, Future}
import scala.annotation.{tailrec, targetName}
import scala.util.control.NonFatal
import State.*
import Result.*

import java.util.concurrent.atomic.AtomicReference

trait Fiber[+T, +E]:
  outer =>
  import Fiber.*
  val state = new AtomicReference[State](New)

  final def join()(using scheduler: Scheduler): Result[T, E] =
    @tailrec
    def loop(): Result[T, E] =
      state.get() match
        case New => Panic(new IllegalStateException("Waiting for fiber that is not started yet"))
        case Running => scheduler.stealTime(); loop()
        case result: Result[?, ?] => result.asInstanceOf[Result[T, E]]
    loop()
  end join

  final def start(using scheduler: Scheduler): Unit =
    if state.compareAndSet(New, Running)
    then scheduler.schedule(this)
  end start

  final def map[R](transform: T => R): Fiber[R, E] = new:
    def run(scheduler: Scheduler): Unit =
      scheduler.schedule(outer)
      state.compareAndSet(
        Running,
        outer.join()(using scheduler).map(transform),
      )
  end map
  
  final def flatMap[R, E0](transform: T => Fiber[R, E0]): Fiber[R, E | E0] = new:
    def run(scheduler: Scheduler): Unit =
      scheduler.schedule(outer)
      state.compareAndSet(
        Running,
        outer.join()(using scheduler)
          .flatMap { value =>
            val fiber = transform(value)
            scheduler.schedule(fiber)
            fiber.join()(using scheduler)
          },
      )
  end flatMap

  final def cancel(): Unit = state.set(Cancelled)
  
  final def cancelled: Boolean = state.get() == Cancelled

  def run(scheduler: Scheduler): Unit

object Fiber:

  inline def defer[T](inline computation: => T): Fiber[T, Nothing] = new:
    override def run(scheduler: Scheduler): Unit =
      state.compareAndSet(Running, Result.lift(computation))
  end defer
  
  inline def panic(inline value: => Throwable): Fiber[Nothing, Nothing] = new:
    state.set(Result.lift(Panic(value)))
    override def run(scheduler: Scheduler): Unit = ()
  end panic

  inline def failed[E](inline value: => E): Fiber[Nothing, E] = new:
    state.set(Result.lift(Fail(value)))
    override def run(scheduler: Scheduler): Unit = ()
  end failed

  inline def cancelled: Fiber[Nothing, Nothing] = new:
    state.set(Cancelled)
    override def run(scheduler: Scheduler): Unit = ()
  end cancelled

  inline def pure[T](inline value: => T): Fiber[T, Nothing] = new:
    state.set(Result.lift(value))
    override def run(scheduler: Scheduler): Unit = ()
  end pure

  inline def fromResult[T, E](inline result: => Result[T, E]): Fiber[T, E] = new:
    state.set(Result.lift(result))
    override def run(scheduler: Scheduler): Unit = ()
  end fromResult

end Fiber
