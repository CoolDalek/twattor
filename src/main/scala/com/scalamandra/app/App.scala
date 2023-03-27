package com.scalamandra.app

import com.scalamandra.concurrent.*

object App:

  def main(args: Array[String]): Unit =
    Scheduler.singleThreaded {
      IO.failed("Hello")
        .onError(_ + " World!")
        .map(println)
        .unsafeRunThrow()
    }

end App
