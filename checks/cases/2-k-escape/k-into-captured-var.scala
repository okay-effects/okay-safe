// A clause keeps `k` in a variable it closes over: the clause is a pure function, the variable a capability.
// (Scala 3.10.1 compiles this: its capture checker misses the polymorphic function; the nightly refuses it.)
import language.experimental.safe
import okay.safe.*
import okay.safe.std.*

object Keep:
  def kept[A](p: A ! Choose): List[A] ! Pure =
    var saved: List[Any] = Nil
    handle(p, Handler.control[Choose, Pure, A, List[A]](a => pure(List(a)))(
      [X] => (op: Choose[X], k: Continuation[X, Pure, List[A]]) => // error: Note that capability `saved` cannot flow into capture set {}
        saved = k :: saved
        k.unwind.map(_ => Nil)))
