// The host forges a runner with `null` — capture checking takes `null` as pure, so a continuation could run with it.
// With explicit nulls it does not compile.
//> flags: -Yexplicit-nulls
//> host: host
package host

import language.experimental.captureChecking
import okay.safe.*

object Forged:
  val p: Int ! Pure = pure[Int, Pure](1).flatMap(x => pure(run(pure[Int, Pure](x))(using null))) // error: Found:    Null
