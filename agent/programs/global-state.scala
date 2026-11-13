// refused: Mutable variable count
// Global mutable state, outside any handler.
import language.experimental.safe
import okay.safe.*
import okay.agent.*

object Counter:
  var count = 0
object Task:
  val program: String ! Tools = pure("done")