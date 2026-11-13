// With safer exceptions, a checked exception needs a `CanThrow` capability, and a continuation is pure: throwing it
// there does not compile.
//> flags: -language:experimental.saferExceptions
import language.experimental.safe
import okay.safe.*

class Refused extends Exception("refused")

object Checked:
  def p(using CanThrow[Refused]): Int ! Pure =
    // error: cannot flow into capture set {}
    pure[Int, Pure](1).flatMap(x => if x > 0 then throw Refused() else pure(x))
