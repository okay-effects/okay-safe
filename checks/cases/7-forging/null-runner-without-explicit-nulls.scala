// The same without explicit nulls: it compiles, and only the machine's run-time check refuses the nested run
// (NestedRun, RuntimeTest). The whole-program check asks explicit nulls of every program unit; this one is host code,
// so it is the host's to compile with them.
//> host: host
package host

import language.experimental.captureChecking
import okay.safe.*

object Forged:
  val p: Int ! Pure = pure[Int, Pure](1).flatMap(x => pure(run(pure[Int, Pure](x))(using null)))
