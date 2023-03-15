package com.scalamandra.concurrent

trait Scheduler:
  
  private[concurrent] def stealTime(): Boolean

  private[concurrent] def schedule[T, E](fiber: Fiber[T, E]): Unit
  
  def shutdown(): Unit

  def shuttingDown: Boolean
  
object Scheduler:
  
  private val default: Scheduler = new:
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
    
  end default
  
  def apply[T](app: Scheduler ?=> T): Unit =
    app(using default)
    default.stealTime()
    default.shutdown()
  end apply
  
end Scheduler
