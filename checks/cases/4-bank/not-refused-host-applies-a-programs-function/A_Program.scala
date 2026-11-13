// THE BOUNDARY, NOT REFUSED: a function the program made, applied by the host to the client. The check sees what
// the host gives a function or a class OF THE PROGRAM; this application is of `Function1.apply`, a library's, so
// the client passes as an `Any` — and the program's `toString` of it runs the client's own. A limit of the rule:
// it reads the static target of a call, not where the function value came from.
package prog

import language.experimental.safe

object Show:
  def show: Any => String = x => x.toString
