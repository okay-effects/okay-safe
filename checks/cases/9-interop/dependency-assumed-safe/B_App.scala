// The program uses it, and compiles. The whole-program check reads the dependency too: its claim is refused, and
// what it does under it.
import language.experimental.safe

object App:
  val stamp: Int -> Long = n => lib.Lib.now() + n
