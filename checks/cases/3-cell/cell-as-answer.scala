// A handler in place gives its cell away as an operation's answer, and the program keeps it. Capture checking lets
// the cell go — a type argument takes it read-only —, but a cell's `get` and `set` are both update methods: a
// read-only cell is inert, and the program that would read the state its path left does not compile. (Before:
// `get` was a plain method, and the escaped cell could be read after the fact.)
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
  // error: Cannot call update method get of c
  def read(c: Cell[Int]): Int ! Pure = pure(c.get)
