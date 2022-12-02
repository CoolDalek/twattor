package com.scalamandra.app

import com.scalamandra.aio.*

object App:

  def main(args: Array[String]): Unit =
    Scheduler {
      async {
        println("Hello!")
      }
    }
    

end App
