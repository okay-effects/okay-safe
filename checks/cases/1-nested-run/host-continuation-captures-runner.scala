// Host code, capture checked: a continuation that would run a program with the host's runner captures the runner,
// and a continuation is a pure function.
//> host: host
package host

import language.experimental.captureChecking
import okay.safe.*

object Nested:
  def go(using r: Runner): Int =
    val p: Int ! Pure = pure[Int, Pure](1).flatMap(x => pure(run(pure[Int, Pure](x)))) // error: Note that capability `r` cannot flow into capture set {}
    run(p)
