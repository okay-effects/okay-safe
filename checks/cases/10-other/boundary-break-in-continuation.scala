// A non-local exit from a continuation: `break` needs its boundary's label, a capability, and a continuation is
// pure. (Run later by the machine, outside its boundary, it would throw through the run.)
import language.experimental.safe
import okay.safe.*
import scala.util.boundary

object Exit:
  def p: Int ! Pure = boundary:
    pure[Int, Pure](1).flatMap(x => boundary.break(pure(x))) // error: cannot flow into capture set {}
