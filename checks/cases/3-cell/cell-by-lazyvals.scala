// `scala.runtime` is a package safe mode assumes safe; its `LazyVals` writes any field of any object by
// `sun.misc.Unsafe`. Given a cell, capture checking stops it: `objCAS` takes a pure `Object`, and a cell is a
// capability. Immutable data is not one: see 10-other/immutable-by-lazyvals.scala.
import language.experimental.safe
import okay.safe.*

object Unsafe:
  def poke(cell: Cell[Int]^): Boolean =
    val offset = scala.runtime.LazyVals.getOffset(classOf[Cell[?]], "state")
    val was: Integer = cell.get
    val now: Integer = 99
    scala.runtime.LazyVals.objCAS(cell, offset, was, now) // error: Note that capability `cell.rd` cannot flow into capture set {}
