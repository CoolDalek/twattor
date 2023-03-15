package com.scalamandra.concurrent

import scala.annotation.{switch, tailrec, threadUnsafe}
import IO.*

import java.util.concurrent.atomic.AtomicLong

sealed trait IO[+T, +E]:
  self =>

  private[concurrent] def tag: IOTag

  inline def unsafeRunThrow()(using Scheduler): T | E =
    unsafeRunSync().getOrThrow()

  inline def unsafeRunSync()(using Scheduler): Result[T, E] =
    unsafeRunAsync().join()

  def unsafeRunAsync()(using sch: Scheduler): Fiber[T, E] =
    val fiber = new Fiber[T, E]:
      override def run(scheduler: Scheduler): Unit =
        IORunLoop[T, E](self, this, scheduler).run()
    fiber.run(sch)
    fiber
  end unsafeRunAsync

  def start: IO[IOFiber[T, E], Nothing] = Thunk(
    sch => IOFiber.wrap(unsafeRunAsync()(using sch))
  )

  def map[R](f: T => R): IO[R, E] =
    Map[T, R, E, E](
      this,
      (_, r) => r.map(f)
    )

  def flatMap[R, E0](f: T => IO[R, E0]): IO[R, E | E0] =
    FlatMap[T, R, E, E | E0](
      this,
      _.isDone,
      (_, r) => f(r.asInstanceOf[Result.Done[T]].value)
    )

  def onCancel[R](fin: IO[Unit, Nothing]): IO[T, E] =
    OnCancel[T, E](
      this,
      fin,
    )

  def recoverPanic[R >: T](recover: PartialFunction[Throwable, R]): IO[R, E] =
    Map[T, R, E, E](
      this,
      (_, r) => r.recoverPanic(recover)
    )

  def recoverPanicWith[R >: T, E0](recover: PartialFunction[Throwable, IO[R, E0]]): IO[R, E | E0] =
    FlatMap[T, R, E, E | E0](
      this,
      x => x.isPanic && recover.isDefinedAt(
        x.asInstanceOf[Result.Panic].error
      ),
      (_, r) => recover(r.asInstanceOf[Result.Panic].error),
    )

  def recover[R >: T](recover: PartialFunction[E, R]): IO[R, E] =
    Map[T, R, E, E](
      this,
      (_, r) => r.recover(recover),
    )

  def recoverWith[R >: T, E0](recover: PartialFunction[E, IO[R, E0]]): IO[R, E | E0] =
    FlatMap[T, R, E, E | E0](
      this,
      x => x.isFail && recover.isDefinedAt(
        x.asInstanceOf[Result.Fail[E]].value
      ),
      (_, r) => recover(r.asInstanceOf[Result.Fail[E]].value),
    )

  def onPanic[R >: T](recover: Throwable => R): IO[R, E] =
    Map[T, R, E, E](
      this,
      (_, r) => r.onPanic(recover),
    )

  def onPanicWith[R >: T, E0](recover: Throwable => IO[R, E0]): IO[R, E | E0] =
    FlatMap[T, R, E, E | E0](
      this,
      x => x.isPanic,
      (_, r) => recover(r.asInstanceOf[Result.Panic].error),
    )

  def onError[R >: T](recover: E => R): IO[R, Nothing] =
    Map[T, R, E, Nothing](
      this,
      (_, r) => r.onFail(recover)
    )

  def onErrorWith[R >: T, E0](recover: E => IO[R, E0]): IO[R, E0] =
    FlatMap[T, R, E, E0](
      this,
      x => x.isFail,
      (_, r) => recover(r.asInstanceOf[Result.Fail[E]].value)
    )

object IO:

  type IOTag = ComputedTag | ThunkTag | FlatMapTag | MapTag | OnCancelTag | FlatMapTag
  type ComputedTag = 0
  inline val ComputedTag: ComputedTag = 0
  type ThunkTag = 1
  inline val ThunkTag: ThunkTag = 1
  type FlatMapTag = 2
  inline val FlatMapTag: FlatMapTag = 2
  type MapTag = 3
  inline val MapTag: MapTag = 3
  type OnCancelTag = 4
  inline val OnCancelTag: OnCancelTag = 4

  type ContinuationTag = FlatMapTag | MapTag
  type ComputationTag = ComputedTag | ThunkTag

  type AnyIO = IO[Any, Any]

  type Bind = Continuation[Any, Any, Any, Any, [_, _] =>> Any]
  sealed trait Continuation[T, R, E, E0, F[_, _]] extends IO[R, E0]:
    def tag: ContinuationTag
    def apply(sch: Scheduler, value: Result[T, E]): F[R, E0]
    inline def erase: Bind = this.asInstanceOf[Bind]
    protected var prev: IO[T, E]
    inline def unlink(): IO[T, E] =
      val ret = prev
      prev = null.asInstanceOf[IO[T, E]]
      ret
    end unlink

  type Compute = Computation[Any, Any]
  sealed trait Computation[T, E] extends IO[T, E]:
    def tag: ComputationTag
    def apply(sch: Scheduler): Result[T, E]
    inline def erase: Compute = this.asInstanceOf[Compute]

  class Computed[T, E](
                        val value: Result[T, E],
                      ) extends Computation[T, E]:
    override def tag: ComputedTag = ComputedTag
    def apply(sch: Scheduler): Result[T, E] = value

  class Pure[T](
                 val value: T,
               ) extends Computation[T, Nothing]:
    override def tag: ComputedTag = ComputedTag
    def apply(sch: Scheduler): Result[T, Nothing] = Result.Done(value)

  class Effect[T, E](
                      val computation: Scheduler => Result[T, E],
                    ) extends Computation[T, E]:
    override def tag: ThunkTag = ThunkTag
    def apply(sch: Scheduler): Result[T, E] =
      Result.lift(computation(sch))

  class Thunk[T](
                  val computation: Scheduler => T,
                ) extends Computation[T, Nothing]:
    override def tag: ThunkTag = ThunkTag
    def apply(sch: Scheduler): Result[T, Nothing] =
      Result.lift(computation(sch))

  class FlatMap[T, R, E, E0](
                              var prev: IO[T, E],
                              val shouldBind: Result[T, E] => Boolean,
                              val bind: (Scheduler, Result[T, E]) => IO[R, E0]
                            ) extends Continuation[T, R, E, E0, IO]:
    override def tag: FlatMapTag = FlatMapTag
    def apply(sch: Scheduler, value: Result[T, E]): IO[R, E0] = safeIO(bind(sch, value))
      
  class Map[T, R, E, E0](
                          protected var prev: IO[T, E], 
                          val bind: (Scheduler, Result[T, E]) => Result[R, E0],
                        ) extends Continuation[T, R, E, E0, Result]:
    override def tag: MapTag = MapTag
    def apply(sch: Scheduler, value: Result[T, E]): Result[R, E0] = Result.lift(bind(sch, value))

  object Map:
    inline def apply[T, R, E, E0](
                                   prev: IO[T, E], 
                                   bind: (Scheduler, Result[T, E]) => Result[R, E0]
                                 ): Map[T, R, E, E0] = new Map[T, R, E, E0](prev, bind)
  end Map

  class OnCancel[T, E](
                        val prev: IO[T, E],
                        val finalizer: IO[Unit, Nothing],
                      ) extends IO[T, E]:
    override def tag: OnCancelTag = OnCancelTag

  inline def safeIO[T, E](inline eval: => IO[T, E]): IO[T, E] =
    Result.lift(eval) match
      case Result.Done(io) => io
      case other => Computed(other.asInstanceOf[Result[T, E]])
  end safeIO

  def apply[T](value: => T): IO[T, Nothing] = Pure(value)

  def failed[E](value: E): IO[Nothing, E] = Computed(Result.Fail(value))

  def panic(value: Throwable): IO[Nothing, Nothing] = Computed(Result.Panic(value))

  @threadUnsafe
  lazy val cancelled: IO[Nothing, Nothing] = Computed(Result.Cancelled)

end IO
