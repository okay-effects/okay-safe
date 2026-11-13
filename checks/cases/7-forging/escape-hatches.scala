// Capture checking's escape hatches, and the compiler's: rejected in safe mode.
import language.experimental.safe
import okay.safe.*

object Hatches:
  def pure1(f: Int => Int): Int -> Int = caps.unsafe.unsafeAssumePure(f) // error: Cannot refer to object unsafe
  class Holder(@caps.unsafe.untrackedCaptures val f: () => Unit) // error: Cannot refer to
