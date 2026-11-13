// A cell that escaped its clause is read-only, and a read-only cell is inert: `set` and `get` are update methods.
import language.experimental.safe
import okay.safe.*

object Escaped:
  def writes(c: Cell[Int]): Unit = c.set(1) // error: Cannot call update method set of c
  def reads(c: Cell[Int]): Int = c.get // error: Cannot call update method get of c
