// A handler in place gives its cell away as an operation's answer: the cell is a capability of the clause, the
// answer a pure value.
import language.experimental.safe
import okay.safe.*

enum Leak[X]:
  case Give() extends Leak[Cell[Int]]

object Leaking:
  def leaked[A](p: A ! Leak): A ! Pure =
    handle(p, Handler.stateful[Leak, Pure, A, A, Int](0)((_, a) => pure(a))(
      [X] => (op: Leak[X], cell: Cell[Int]^) => op match
        case Leak.Give() => cell)) // error: Found
