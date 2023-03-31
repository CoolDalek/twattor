package com.scalamandra.experimental

import cats.{Monad, MonadThrow}
import cats.syntax.all.*
import cats.data.ContT
import cats.free.Free

import java.lang.invoke.VarHandle
import scala.annotation.tailrec

object IO:

  enum Computation[+T]:
    case Value[R](value: R) extends Computation[R]
    case Error(error: Throwable) extends Computation[Nothing]

  given MonadThrow[Computation] with

    override def raiseError[A](e: Throwable): Computation[A] =
      Computation.Error(e)

    override def handleErrorWith[A](fa: Computation[A])(f: Throwable => Computation[A]): Computation[A] =
      fa match
        case Computation.Error(e) => f(e)
        case _ => fa

    override def pure[A](x: A): Computation[A] = Computation.Value(x)

    override def flatMap[A, B](fa: Computation[A])(f: A => Computation[B]): Computation[B] =
      fa match
        case Computation.Value(x) => f(x)
        case err: Computation.Error => err
    end flatMap

    override def tailRecM[A, B](a: A)(f: A => Computation[Either[A, B]]): Computation[B] =

      @tailrec
      def loop(a: A): Computation[B] =
        f(a) match
          case Computation.Value(value) =>
            value match
              case Right(done) =>
                Computation.Value(done)
              case Left(next) =>
                loop(next)
          case err: Computation.Error => err
      end loop

      loop(a)
    end tailRecM

  end given

  private type Suspension[T] = Free[Computation, T]
  type IO[+T] = ContT[Suspension, Unit, T]

  inline def pure[T](value: T): IO[T] = ContT.pure(value)

  inline def defer[T](value: => T): IO[T] = ContT.defer(value)

  def async[T](f: (Either[Throwable, T] => Unit) => Unit): IO[T] =
    for
      either <- ContT[Suspension, Unit, Computation[T]] { (cont: Computation[T] => Suspension[Unit]) =>
        ContT.defer {
          val cb = (result: Either[Throwable, T]) => {
            VarHandle.fullFence()
            val comp = result ma
            cont(result).runTailRec
            ()
          }
          VarHandle.fullFence()
          f(cb)
        }
      }
      result <- ContT.liftF[Suspension, Unit, T](Free.liftF(either))
    yield result
  end async

  extension [T](self: IO[T]) {

    inline def map[R](f: T => R): IO[R] = self.map(f)

    inline def flatMap[R](f: T => IO[R]): IO[R] = self.flatMap(f)

    inline def zip[R](other: IO[R]): IO[(T, R)] =
      for
        t <- self
        r <- other
      yield (t, r)

  }
  
export IO.IO
