// refused: scala.util.Random
// Ambient, assumed safe by safe mode: random numbers. The whole-program check refuses them.
import language.experimental.safe
import okay.safe.*
import okay.agent.*

object Task:
  val program: String ! Tools = Log.line("hi").map(_ => scala.util.Random.nextInt().toString)