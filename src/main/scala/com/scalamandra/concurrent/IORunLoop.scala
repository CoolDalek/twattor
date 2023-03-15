package com.scalamandra.concurrent

import scala.collection.mutable
import scala.annotation.*
import IO.*

class IORunLoop[T, E](
                       var current: AnyIO,
                       val fiber: Fiber[T, E],
                       val scheduler: Scheduler,
                       val continuations: mutable.Stack[Bind],
                     ):
  private var loop = false
  private val finalizers = mutable.ArrayBuffer.empty[IO[Unit, Nothing]]
  type AnyFlatMap = FlatMap[Any, Any, Any, Any]
  type AnyMap = Map[Any, Any, Any, Any]

  @tailrec
  private def tryComplete(value: Result[Any, Any]): Unit =
    if continuations.isEmpty then
      loop = false
      fiber.state = value.asInstanceOf[Result[T, E]]
    else
      val bind = continuations.pop()
      (bind.tag: @switch) match
        case MapTag =>
          val next = bind.asInstanceOf[AnyMap](scheduler, value)
          tryComplete(next)
        case FlatMapTag =>
          val flatMap = bind.asInstanceOf[AnyFlatMap]
          if flatMap.shouldBind(value)
          then current = flatMap.bind(scheduler, value)
          else tryComplete(value)
    end if
  end tryComplete

  def run(): Unit =
    loop = true
    while loop do
      ((current.tag: Int): @switch) match
        case ComputedTag | ThunkTag =>
          tryComplete(
            current.asInstanceOf[Compute](scheduler)
          )
        case FlatMapTag | MapTag =>
          val bind = current.asInstanceOf[Bind]
          current = bind.unlink()
          continuations.push(bind.erase)
        case OnCancelTag =>
          val onCancel = current.asInstanceOf[OnCancel[Any, Any]]
          current = onCancel.prev
          finalizers.addOne(onCancel.finalizer)
      end match
    end while
    if fiber.state.asInstanceOf[Result[T, E]].isCanceled then
      finalizers.map(_.unsafeRunAsync()(using scheduler))
        .foreach(_.join()(using scheduler))
  end run

object IORunLoop:

  inline def apply[T, E](
                          io: IO[T, E],
                          fiber: Fiber[T, E],
                          scheduler: Scheduler,
                          continuations: mutable.Stack[Bind] = mutable.Stack.empty[Bind],
                        ): IORunLoop[T, E] =
    new IORunLoop(io, fiber, scheduler, continuations)

end IORunLoop
