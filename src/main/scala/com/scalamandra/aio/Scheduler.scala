package com.scalamandra.aio

trait Scheduler:
  
  private[aio] def stealTime(): Boolean

  private[aio] def spinThreshold: Int

  private[aio] def schedule[T](fiber: Fiber[T]): Unit
  
  def shutdown(): Unit

  def shuttingDown: Boolean
  
object Scheduler:
  
  private val default: Scheduler = new:
    import scala.collection.mutable
    private var working = true
    private val queue = mutable.Queue.empty[Fiber[?]]

    def stealTime(): Boolean =
      if(working && queue.nonEmpty)
        queue.dequeue().run(this); true
      else false
    
    def spinThreshold: Int = 16
    
    def schedule[T](fiber: Fiber[T]): Unit =
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
