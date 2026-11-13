// refused: Cannot refer to method run
// A program that runs a program: it has no runner, and cannot name run.
import language.experimental.safe
import okay.safe.*
import okay.agent.*

object Task:
  def inner(using Runner): String = okay.safe.run(pure[String, Pure]("inner"))
  val program: String ! Tools = pure("done")