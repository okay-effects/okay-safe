// THE BOUNDARY, CONSERVATIVE: the program's own lambdas, chosen among by the kernel's `Choose`. The answer of an
// operation is what its handler gives, and the check does not know whose handler that is — the host's may answer
// with a function of its own —, so a word answered with a function is refused even when the program made both.
// Pass data instead (an index, an enum) and keep the functions in the program.
import language.experimental.safe
import okay.safe.*
import okay.safe.std.*

object Pick:
  val inc: Int -> Int = _ + 1
  val dec: Int -> Int = _ - 1
  def either[R[_]: Has[Choose]](n: Int): Int ! R =
    // whole: an effect's answer must be first-order data: a function
    Choose.among(inc, dec).map(f => f(n))
  def byIndex[R[_]: Has[Choose]](n: Int): Int ! R = Choose.among(0, 1).map(i => if i == 0 then inc(n) else dec(n))
