// A cell made or written from outside the kernel: its constructor and its state are the kernel's.
import language.experimental.safe
import okay.safe.*

object Outside:
  def poke(cell: Cell[Int]^): Unit =
    cell.state = 1 // error: variable state cannot be accessed
  def make: Cell[Int]^ = new Cell[Int](0) {} // error: constructor Cell cannot be accessed
