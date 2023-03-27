package com.scalamandra.concurrent

trait Scheduler:
  
  private[concurrent] def stealTime(): Boolean

  private[concurrent] def schedule[T, E](fiber: Fiber[T, E]): Unit
  
  def shutdown(): Unit

  def shuttingDown: Boolean
  
object Scheduler:
  
  private val singleThreaded: Scheduler = new:
    import scala.collection.mutable
    private var working = true
    private val queue = mutable.Queue.empty[Fiber[?, ?]]

    def stealTime(): Boolean =
      if(working && queue.nonEmpty)
        queue.dequeue().run(this); true
      else false
    
    def schedule[T, E](fiber: Fiber[T, E]): Unit =
      if(working) queue.enqueue(fiber)
    
    def shutdown(): Unit = working = false
    
    def shuttingDown: Boolean = !working
    
  end singleThreaded

  def singleThreaded[T](app: Scheduler ?=> T): Unit =
    try
      app(using singleThreaded)
      singleThreaded.stealTime()
    finally singleThreaded.shutdown()
  end singleThreaded

  private val multithreaded: Scheduler = new:

    def stealTime(): Boolean = ???

    def schedule[T, E](fiber: Fiber[T, E]): Unit = ???

    def shutdown(): Unit = ???

    def shuttingDown: Boolean = ???

  end multithreaded

end Scheduler
