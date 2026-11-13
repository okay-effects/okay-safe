// A program that says it is in the kernel's package gets its package-private parts: a cell's state written from
// outside any clause. The compiler cannot refuse it — `private[safe]` is open to every file that says
// `package okay.safe` —, the whole-program check does; on the module path, a split package (RuntimeTest).
package okay.safe // whole: declares into the kernel's package okay.safe

import language.experimental.safe

object Forged:
  def poke(cell: Cell[Int]^): Unit = cell.state = 99
