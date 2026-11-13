// The same cell given away typed exclusive (`Leak[Cell[Int]^]`): the clause compiles, and the run's answer holds
// the cell — but nothing in a program can use it: the exclusive reference stands for a root capability made in
// the effect's case, which no program may use.
import language.experimental.safe
import okay.safe.*

enum Leak[X]:
  case Give() extends Leak[Cell[Int]^]

object Leaking:
  def leaked[A](p: A ! Leak): A ! Pure =
    handle(p, Handler.stateful[Leak, Pure, A, A, Int](0)((_, a) => pure(a))(
      [X] => (op: Leak[X], cell: Cell[Int]^) => op match
        case _: Leak.Give => cell))
  def write(c: Cell[Int]^): Int ! Leak = { c.set(7); pure(c.get) }
  // error: is not included in the allowed capture set
  val written: Int ! Pure = leaked(Comp.Bind(word(Leak.Give()).into[Leak], write))
