// The positive counterpart: the cell read and written in its clause, and nowhere else.
import language.experimental.safe
import okay.safe.*
import okay.safe.std.*

object Counting:
  def counted[A](p: A ! (State % Int)): (Int, A) ! Pure =
    handle(p, Handler.stateful[State % Int, Pure, A, (Int, A), Int](0)((s, a) => pure((s, a)))(
      [X] => (op: State[Int, X], cell: Cell[Int]^) => op match
        case _: State.Get[?] => cell.get
        case State.Put(s) => cell.set(s)))
