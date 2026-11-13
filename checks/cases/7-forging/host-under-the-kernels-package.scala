// A host that declares itself under the kernel's package: `private[safe]` reaches every package under `okay.safe`,
// so it can make its own runner (and a program there, a cell). The compiler cannot refuse it — package-private
// access is Scala's —; the whole-program check refuses any unit there, the host's too, and the gate seals nothing
// that has one. (This is why the example application is `okay.bank`, not `okay.safe.example`.)
//> host: okay.safe.mine
package okay.safe.mine // whole: declares into the kernel's package okay.safe.mine

import language.experimental.captureChecking
import okay.safe.*

object Sneak:
  val p: Int ! Pure = pure[Int, Pure](1).flatMap(x => pure(Runner.host[Int](x)))
