package com.scalamandra.concurrent

object IOFiber:
  opaque type IOFiber[+T, +E] = Fiber[T, E]

  extension [T, E](self: IOFiber[T, E]) {

    def join: IO[T, E] = IO.Effect(
      sch => self.join()(using sch)
    )

  }

  inline def wrap[T, E](fiber: Fiber[T, E]): IOFiber[T, E] = fiber

export IOFiber.IOFiber
