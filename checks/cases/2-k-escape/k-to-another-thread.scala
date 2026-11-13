// `k` given to another thread: threads, executors and futures cannot be named in safe mode.
import language.experimental.safe
import okay.safe.*

object Elsewhere:
  def away(k: Continuation[Int, Pure, Int]): Unit =
    // error: Cannot refer to class Thread in package java.lang from safe code since it is neither compiled in safe
    Thread(() => { val _ = k(1) }).start()
  def later(k: Continuation[Int, Pure, Int]) =
    // error: Cannot refer to method apply in object Future
    scala.concurrent.Future(k(1))(using scala.concurrent.ExecutionContext.global)
