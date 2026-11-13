// refused: throw: an exception is an effect unsaid
// An exception thrown: a failure the types do not say. The whole-program check refuses it.
import language.experimental.safe
import okay.safe.*
import okay.agent.*

object Task:
  val program: String ! Tools = Log.line("hi").map(_ => throw IllegalStateException("boom"))