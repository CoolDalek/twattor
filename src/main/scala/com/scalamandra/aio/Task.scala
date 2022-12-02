package com.scalamandra.aio

opaque type Task[T] = Fiber[T]
opaque type TaskFiber[T] = Fiber[T]
object Task:

  extension [T](self: Task[T]) {
    
    inline def fork(using Scheduler): TaskFiber[T] =
      self.start
      self
    end fork

    inline def awaitResult(using Scheduler): Result[T] =
      self.start
      self.join
    end awaitResult

    inline def await(using Scheduler): T =
      awaitResult.getOrThrow

    inline def map[R](transform: T => R): Task[R] =
      self.map(transform)

    inline def flatMap[R](transform: T => Task[R]): Task[R] =
      self.flatMap(transform)

  }
  
  extension [T](self: TaskFiber[T]) {
    
    inline def joinResult(using Scheduler): Result[T] =
      self.join
    
    inline def join(using Scheduler): T =
      join.getOrThrow
    
  }
  
  inline def pure[T](inline value: => T): Task[T] = Fiber.pure(value)
  
  inline def defer[T](inline computation: => T): Task[T] = Fiber.defer(computation)

export Task.defer as async
