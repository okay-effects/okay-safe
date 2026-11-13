// The host gives a program the table that holds the resources. Safe mode does not check a parameter's type, nor
// a parameter's instance members, so this compiles: the program reads the host's resources past the handler. The
// whole-program check refuses the type (the boundary rule) and the call (the table is the host's).
import language.experimental.safe
import okay.safe.*

object Peek:
  // whole: okay.safe.Table
  def peek(t: Table[List, String], id: Id[List]): Option[String] =
    t.get(id) // whole: okay.safe.Table.get
