// The cell given away as an operation's answer, and kept but never read: it compiles (cell-as-answer: the read does
// not). The boundary rule refuses the escape itself — a cell is no data an effect may answer with: it is neither
// final nor sealed, and it is the kernel's state.
import language.experimental.safe
import okay.safe.*

enum Leak[X]:
  case Give() extends Leak[Cell[Int]] // whole: the answer of Give, an operation of Leak, must be first-order data

object Leaking:
  def leaked[A](p: A ! Leak): A ! Pure =
    handle(p, Handler.stateful[Leak, Pure, A, A, Int](0)((_, a) => pure(a))(
      [X] => (op: Leak[X], cell: Cell[Int]^) => op match
        case _: Leak.Give => cell))
  // whole: an effect's answer must be first-order data: okay.safe.Cell
  val kept: Cell[Int] ! Pure = leaked(word(Leak.Give()).into[Leak])
