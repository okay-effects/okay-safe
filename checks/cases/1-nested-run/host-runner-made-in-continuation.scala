// What capture checking cannot refuse: a host continuation that makes a runner of its own. `Runner.host` is a pure
// static method, and the runner it makes does not escape it. It compiles, and host code is not held to the rules of
// programs; the machine refuses the nested run at run time (NestedRun, RuntimeTest).
//> host: host
package host

import language.experimental.captureChecking
import okay.safe.*

object Own:
  val p: Int ! Pure = pure[Int, Pure](1).flatMap(x => pure(Runner.host(run(pure[Int, Pure](x)))))
