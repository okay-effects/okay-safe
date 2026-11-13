// refused: Cannot refer to object unsafe in package scala.caps
// An escape hatch of capture checking.
import language.experimental.safe
import okay.safe.*
import okay.agent.*

object Task:
  val program: String ! Tools = pure(caps.unsafe.unsafeAssumePure("x"))