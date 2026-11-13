// The positive counterpart: a failure is an effect, and its handler unwinds the rest — finalizers run.
import language.experimental.safe
import okay.safe.*
import okay.safe.std.*

object Failing:
  def divide[R[_]: Has[Throws % String]](a: Int, b: Int): Int ! R =
    if b == 0 then Throws.raise[String]("division by zero").into[R] else pure(a / b)
  val result: Either[String, Int] ! Pure = Throws.run(divide[Throws % String](1, 0))
