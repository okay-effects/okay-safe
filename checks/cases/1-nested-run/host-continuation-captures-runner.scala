// Host code, capture checked: a continuation that would run a program with the runner its entry was given captures
// the runner, and a continuation is a pure function.
//> host: host
package host

import language.experimental.captureChecking
import okay.safe.*

object Nested extends Entry:
  def run(args: List[String])(using r: Runner): Unit =
    // error: Note that capability `r` cannot flow into capture set {}
    val p: Int ! Pure = pure[Int, Pure](1).flatMap(x => pure(okay.safe.run(pure[Int, Pure](x))))
    okay.safe.run(p): Unit
