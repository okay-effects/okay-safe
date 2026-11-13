// A clause given its cell read-only cannot write it: `set` is an update method, and asks the exclusive capability.
import language.experimental.safe
import okay.safe.*

object ReadOnly:
  def write(cell: Cell[Int]): Unit = cell.set(1) // error: Cannot call update method set
