// The positive counterpart: `k` returned from its clause, after the handler has returned, is a value — immutable
// frames, resumed later as often as wanted; nothing is replayed (checks: RuntimeTest).
import language.experimental.safe
import okay.safe.*
import okay.safe.std.*

object Later:
  type Rest = Either[Int, Continuation[Int, Pure, Either[Int, Any]]]
  def suspended(p: Int ! Choose): Either[Int, Any] ! Pure =
    handle(p, Handler.control[Choose, Pure, Int, Either[Int, Any]](a => pure(Left(a)))(
      [X] => (op: Choose[X], k: Continuation[X, Pure, Either[Int, Any]]) => pure(Right(k))))
