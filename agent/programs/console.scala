// refused: Cannot refer to method println
// Ambient: the console. Safe mode refuses it.
import language.experimental.safe
import okay.safe.*
import okay.agent.*

object Task:
  val program: String ! Tools = Log.line("hi").map(_ => { println("leaked"); "done" })