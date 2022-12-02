package com.scalamandra.aio

import scala.concurrent.Future
import scala.annotation.tailrec
import scala.util.control.NonFatal

private[aio] trait Fiber[+T]:
  outer =>
  import Fiber.*
  protected var state: State = New

  final def join(using scheduler: Scheduler): Result[T] =
    @tailrec
    def loop(idle: Int): Result[T] =
      state match
        case New => Fail(new IllegalStateException("Waiting for fiber that is not started yet"))
        case Running =>
          if(scheduler.stealTime()) loop(0)
          else {
            if (scheduler.spinThreshold >= idle) Thread.onSpinWait()
            loop(idle + 1)
          }
        case result: Result[?] => result.asInstanceOf[Result[T]]
    loop(0)
  end join

  final def start(using scheduler: Scheduler): Unit =
    if(state == New)
      state = Running
      scheduler.schedule(this)
  end start

  final def map[R](transform: T => R): Fiber[R] = new:
    def run(scheduler: Scheduler): Unit =
      scheduler.schedule(outer)
      state = outer.join(using scheduler).map(transform)
  end map
  
  final def flatMap[R](transform: T => Fiber[R]): Fiber[R] = new:
    def run(scheduler: Scheduler): Unit =
      scheduler.schedule(outer)
      state = outer.join(using scheduler)
        .flatMap { value =>
          val fiber = transform(value)
          scheduler.schedule(fiber)
          fiber.join(using scheduler)
        }
  end flatMap

  def run(scheduler: Scheduler): Unit

private[aio] object Fiber:

  sealed trait State

  private case object New extends State
  private case object Running extends State

  sealed trait Result[+T] extends State:

    inline def map[R](transform: T => R): Result[R] =
      this match
        case Done(value) => liftResult(transform(value))
        case fail: Fail => fail
    end map

    inline def flatten[R](using T <:< Result[R]): Result[R] =
      this match
        case Done(value) => value.asInstanceOf[Result[R]]
        case fail: Fail => fail
    end flatten

    inline def flatMap[R](transform: T => Result[R]): Result[R] =
      map(transform).flatten

    inline def getOrThrow: T =
      this match
        case Done(value) => value
        case Fail(exc) => throw exc
    end getOrThrow

  end Result

  case class Done[T](value: T) extends Result[T]
  case class Fail(error: Throwable) extends Result[Nothing]

  inline def defer[T](inline computation: => T): Fiber[T] = new:
    override def run(scheduler: Scheduler): Unit = state = liftResult(computation)
  end defer

  inline def pure[T](inline value: => T): Fiber[T] = new:
    state = liftResult(value)
    override def run(scheduler: Scheduler): Unit = ()
  end pure

  inline def fromResult[T](inline result: => Result[T]): Fiber[T] = new:
    state = result
    override def run(scheduler: Scheduler): Unit = ()
  end fromResult

  inline def liftResult[T](inline computation: => T): Result[T] =
    try Done(computation) catch case exc if NonFatal(exc) => Fail(exc)

export Fiber.{Result, Done, Fail}
