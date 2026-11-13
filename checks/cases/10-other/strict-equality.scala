// An operation compared with a value of an unrelated type is always false; with strict equality, a compile error.
//> flags: -language:strictEquality
import language.experimental.safe
import okay.safe.std.*

object Compared:
  def same(op: State[Int, Int]): Boolean = op == 1 // error: cannot be compared
