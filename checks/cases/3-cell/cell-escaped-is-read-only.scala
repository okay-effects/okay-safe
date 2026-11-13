// A cell that escaped its clause is read-only: `set` is an update method.
import language.experimental.safe
import okay.safe.*

object Escaped:
  def writes(c: Cell[Int]): Unit = c.set(1) // error: Cannot call update method set of c
