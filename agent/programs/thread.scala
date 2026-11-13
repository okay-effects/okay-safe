// refused: Thread
// A thread of its own.
import language.experimental.safe
import okay.safe.*
import okay.agent.*

object Task:
  val program: String ! Tools = pure(Thread(() => ()).getName)