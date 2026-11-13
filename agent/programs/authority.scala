// refused: Cannot refer to method tail in object Authority
// A handler holding an authority, made by the program: the host makes those.
import language.experimental.safe
import okay.safe.*
import okay.agent.*

object Task:
  val h = Authority.tail[Log, Pure, String, String](pure)([X] => (op: Log[X]) => op match
    case Log.Line(_) => ())
  val program: String ! Tools = pure("done")