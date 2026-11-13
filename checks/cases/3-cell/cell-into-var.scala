// The clause keeps its cell in a variable it closes over.
import language.experimental.safe
import okay.safe.*
import okay.safe.std.*

object Keeping:
  def kept[A](p: A ! (State % Int)): A ! Pure =
    var saved: Any = ()
    handle(p, Handler.stateful[State % Int, Pure, A, A, Int](0)((_, a) => pure(a))(
      [X] => (op: State[Int, X], cell: Cell[Int]^) => // error: Found
        saved = cell
        op match
          case _: State.Get[?] => cell.get
          case State.Put(s) => cell.set(s)))
