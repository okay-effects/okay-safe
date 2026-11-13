// A package clause nested in a file of another package: `package okay.safe { ... }` inside a program's file. The
// whole-program check reads every package clause, not the file's first alone (before, it read only that one).
import language.experimental.safe

package okay.safe { // whole: declares into the kernel's package okay.safe
  object Forged:
    val n: Int = 1
}

object Outside:
  val m: Int = 2
