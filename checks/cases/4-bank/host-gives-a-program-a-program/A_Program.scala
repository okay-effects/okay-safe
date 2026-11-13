// THE BOUNDARY: the host gives a program a PROGRAM it built — a value of the kernel's, but one whose binds are the
// host's lambdas, which may hold the client: the program could run it as often as it likes. A program is not
// first-order data; the host gives programs to the kernel's functions (`handle`, `audit`), not to a program's.
package prog

import language.experimental.safe
import okay.safe.*

object Twice:
  def twice(p: Int ! Pure): Int ! Pure = p.flatMap(a => p.map(b => a + b))
