// A host continuation that would make a runner of its own: a runner is given, never made — its constructor is
// private, `Runner.host` is the kernel's, and `Launch`, which gives it once to the application's entry, cannot be
// named. So there is nothing to call. (Before: `Runner.host` was public, and only NestedRun stopped this.)
//> host: host
package host

import language.experimental.captureChecking
import okay.safe.*

object Own:
  // error: method host cannot be accessed as a member of okay.safe.Runner.type
  val p: Int ! Pure = pure[Int, Pure](1).flatMap(x => pure(Runner.host[Int](x)))
  // error: constructor Runner cannot be accessed
  val q: Int ! Pure = pure[Int, Pure](1).flatMap(x => pure(run(pure[Int, Pure](x))(using new Runner())))
  // error: object Launch cannot be accessed as a member of okay.safe
  val r: Int ! Pure = pure[Int, Pure](1).flatMap(x => { okay.safe.Launch.main(Array()); pure(x) })
