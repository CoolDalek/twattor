package com.scalamandra.concurrent

import scala.annotation.targetName
import scala.concurrent.CancellationException
import scala.util.control.NonFatal

sealed trait State  
object State:

  case object New extends State
  case object Running extends State

  sealed trait Result[+T, +E] extends State:
    def map[R](transform: T => R): Result[R, E]
    def flatten[R, E0](using T <:< Result[R, E0]): Result[R, E | E0]
    def flatMap[R, E0](transform: T => Result[R, E0]): Result[R, E | E0]
    def recoverPanic[R >: T](recover: PartialFunction[Throwable, R]): Result[R, E]
    def recoverPanicWith[R >: T, E0](recover: PartialFunction[Throwable, Result[R, E0]]): Result[R, E | E0]
    def recover[R >: T](recover: PartialFunction[E, R]): Result[R, E]
    def recoverWith[R >: T, E0](recover: PartialFunction[E, Result[R, E0]]): Result[R, E | E0]
    def onPanic[R >: T](recover: Throwable => R): Result[R, E]
    def onPanicWith[R >: T, E0](recover: Throwable => Result[R, E0]): Result[R, E | E0]
    def onFail[R >: T](recover: E => R): Result[R, Nothing]
    def onFailWith[R >: T, E0](recover: E => Result[R, E0]): Result[R, E0]
    def getOrThrow(): T | E
    def isDone: Boolean
    def isFail: Boolean
    def isPanic: Boolean
    def isCanceled: Boolean
  object Result:

    case class Done[T](value: T) extends Result[T, Nothing]:
      def map[R](transform: T => R): Result[R, Nothing] = lift(transform(value))
      def flatten[R, E0](using ev: T <:< Result[R, E0]): Result[R, E0] = ev(value)
      def flatMap[R, E0](transform: T => Result[R, E0]): Result[R, E0] = map(transform).flatten
      def getOrThrow(): T = value

      def recoverPanic[R >: T](recover: PartialFunction[Throwable, R]): Result[R, Nothing] = this
      def recoverPanicWith[R >: T, E0](recover: PartialFunction[Throwable, Result[R, E0]]): Result[R, E0] = this
      def recover[R >: T](recover: PartialFunction[Nothing, R]): Result[R, Nothing] = this
      def recoverWith[R >: T, E0](recover: PartialFunction[Nothing, Result[R, E0]]): Result[R, E0] = this
      def onPanic[R >: T](recover: Throwable => R): Result[R, Nothing] = this
      def onPanicWith[R >: T, E0](recover: Throwable => Result[R, E0]): Result[R, E0] = this
      def onFail[R >: T](recover: Nothing => R): Result[R, Nothing] = this
      def onFailWith[R >: T, E0](recover: Nothing => Result[R, E0]): Result[R, E0] = this

      def isDone: Boolean = true
      def isFail: Boolean = false
      def isPanic: Boolean = false
      def isCanceled: Boolean = false
    end Done

    case class Fail[E](value: E) extends Result[Nothing, E]:
      def recover[R >: Nothing](recover: PartialFunction[E, R]): Result[R, E] =
        if recover.isDefinedAt(value)
        then lift(recover(value))
        else this
      def recoverWith[R >: Nothing, E0](recover: PartialFunction[E, Result[R, E0]]): Result[R, E | E0] =
        if recover.isDefinedAt(value)
        then lift(recover(value))
        else this
      def onFail[R >: Nothing](recover: E => R): Result[R, Nothing] = lift(recover(value))
      def onFailWith[R >: Nothing, E0](recover: E => Result[R, E0]): Result[R, E0] = lift(recover(value))
      def getOrThrow(): E = value

      def map[R](transform: Nothing => R): Result[R, E] = this
      def flatten[R, E0](using Nothing <:< Result[R, E0]): Result[R, E | E0] = this
      def flatMap[R, E0](transform: Nothing => Result[R, E0]): Result[R, E | E0] = this
      def recoverPanic[R >: Nothing](recover: PartialFunction[Throwable, R]): Result[R, E] = this
      def recoverPanicWith[R >: Nothing, E0](recover: PartialFunction[Throwable, Result[R, E0]]): Result[R, E | E0] = this
      def onPanic[R >: Nothing](recover: Throwable => R): Result[R, E] = this
      def onPanicWith[R >: Nothing, E0](recover: Throwable => Result[R, E0]): Result[R, E | E0] = this

      def isDone: Boolean = false
      def isFail: Boolean = true
      def isPanic: Boolean = false
      def isCanceled: Boolean = false
    end Fail

    case class Panic(error: Throwable) extends Result[Nothing, Nothing]:
      def recoverPanic[R >: Nothing](recover: PartialFunction[Throwable, R]): Result[R, Nothing] =
        if recover.isDefinedAt(error)
        then lift(recover(error))
        else this
      def recoverPanicWith[R >: Nothing, E0](recover: PartialFunction[Throwable, Result[R, E0]]): Result[R, E0] =
        if recover.isDefinedAt(error)
        then lift(recover(error))
        else this
      def onPanic[R >: Nothing](recover: Throwable => R): Result[R, Nothing] = lift(recover(error))
      def onPanicWith[R >: Nothing, E0](recover: Throwable => Result[R, E0]): Result[R, E0] = lift(recover(error))
      def getOrThrow(): Nothing | Nothing = throw error

      def map[R](transform: Nothing => R): Result[R, Nothing] = this
      def flatten[R, E0](using Nothing <:< Result[R, E0]): Result[R, E0] = this
      def flatMap[R, E0](transform: Nothing => Result[R, E0]): Result[R, E0] = this
      def recover[R >: Nothing](recover: PartialFunction[Nothing, R]): Result[R, Nothing] = this
      def recoverWith[R >: Nothing, E0](recover: PartialFunction[Nothing, Result[R, E0]]): Result[R, E0] = this
      def onFail[R >: Nothing](recover: Nothing => R): Result[R, Nothing] = this
      def onFailWith[R >: Nothing, E0](recover: Nothing => Result[R, E0]): Result[R, E0] = this

      def isDone: Boolean = false
      def isFail: Boolean = false
      def isPanic: Boolean = true
      def isCanceled: Boolean = false
    end Panic

    type Cancelled = Cancelled.type
    case object Cancelled extends Result[Nothing, Nothing]:
      def getOrThrow(): Nothing = throw new CancellationException("Computation was cancelled")
      
      def map[R](transform: Nothing => R): Result[R, Nothing] = this
      def flatten[R, E0](using Nothing <:< Result[R, E0]): Result[R, E0] = this
      def flatMap[R, E0](transform: Nothing => Result[R, E0]): Result[R, E0] = this
      def recoverPanic[R >: Nothing](recover: PartialFunction[Throwable, R]): Result[R, Nothing] = this
      def recoverPanicWith[R >: Nothing, E0](recover: PartialFunction[Throwable, Result[R, E0]]): Result[R, E0] = this
      def recover[R >: Nothing](recover: PartialFunction[Nothing, R]): Result[R, Nothing] = this
      def recoverWith[R >: Nothing, E0](recover: PartialFunction[Nothing, Result[R, E0]]): Result[R, E0] = this
      def onPanic[R >: Nothing](recover: Throwable => R): Result[R, Nothing] = this
      def onPanicWith[R >: Nothing, E0](recover: Throwable => Result[R, E0]): Result[R, E0] = this
      def onFail[R >: Nothing](recover: Nothing => R): Result[R, Nothing] = this
      def onFailWith[R >: Nothing, E0](recover: Nothing => Result[R, E0]): Result[R, E0] = this

      def isDone: Boolean = false
      def isFail: Boolean = false
      def isPanic: Boolean = false
      def isCanceled: Boolean = true
    end Cancelled
      
    @targetName("liftIntoResult")
    inline def lift[T, E](inline computation: => T): Result[T, E] =
      try Done(computation) catch case exc if NonFatal(exc) => Panic(exc)

    @targetName("liftResultItself")
    inline def lift[T, E](inline computation: => Result[T, E]): Result[T, E] =
      try computation catch case exc if NonFatal(exc) => Panic(exc)

  end Result

export State.Result
