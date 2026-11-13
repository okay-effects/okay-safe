// The positive counterpart: state is an effect, kept by its handler; each path of a multi-shot program its own.
import language.experimental.safe
import okay.safe.*
import okay.safe.std.*

object Counted:
  def tick[R[_]: Has[State % Int]]: Int ! R = State.get[Int].flatMap(n => State.put(n + 1).map(_ => n))
  val twice: (Int, Int) ! Pure = State.run(0)(tick[State % Int].flatMap(_ => tick[State % Int]))
