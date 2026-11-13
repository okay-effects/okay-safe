// `k` kept in a global variable: a global variable does not compile at all in safe mode.
import language.experimental.safe
import okay.safe.*

object Global:
  // error: Mutable variable k is defined in a class that does not extend `Stateful` or `Mutable`
  var k: Option[Continuation[Int, Pure, Int]] = None
