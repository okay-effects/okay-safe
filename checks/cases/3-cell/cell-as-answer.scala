// NOT BLOCKED: a handler in place gives its cell away as an operation's answer, and the program returns it out of
// the run. Capture checking lets a cell go as `Cell[Int]` — read-only —, and the whole-program check sees nothing
// to refuse: a reader of the escaped cell sees the state its path left. Writing it is refused
// (cell-escaped-is-read-only.scala).
import language.experimental.safe
import okay.safe.*

enum Leak[X]:
  case Give() extends Leak[Cell[Int]]

object Leaking:
  def leaked[A](p: A ! Leak): A ! Pure =
    handle(p, Handler.stateful[Leak, Pure, A, A, Int](0)((_, a) => pure(a))(
      [X] => (op: Leak[X], cell: Cell[Int]^) => op match
        case _: Leak.Give => cell))
  val kept: Cell[Int] ! Pure = leaked(word(Leak.Give()).into[Leak])
