// refused: java.time.Instant.now: the time now
// Ambient, assumed safe by safe mode: the clock by java.time. The whole-program check refuses it.
import language.experimental.safe
import okay.safe.*
import okay.agent.*

object Task:
  val program: String ! Tools = Log.line("hi").map(_ => java.time.Instant.now().toString)