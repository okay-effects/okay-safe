// `k` given to another thread: threads, executors and futures cannot be named in safe mode.
import language.experimental.safe
import okay.safe.*

object Elsewhere:
  def away(k: Continuation[Int, Pure, Int]): Unit =
    Thread(() => { val _ = k(1) }).start() // error: Cannot refer to class Thread in package java.lang from safe code since it is neither compiled in safe mode nor tagged with @assumedSafe
  def later(k: Continuation[Int, Pure, Int]) =
    scala.concurrent.Future(k(1))(using scala.concurrent.ExecutionContext.global) // error: Cannot refer to method apply in object Future
