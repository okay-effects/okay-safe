// An unchecked exception thrown from a continuation, and one caught: safe mode allows both — `Throwable` and its
// usual subclasses are assumed safe. The whole-program check refuses both: a failure is an effect (Throws).
import language.experimental.safe
import okay.safe.*

object Thrown:
  val p: Int ! Pure = pure[Int, Pure](1).flatMap(x => if x > 0 then throw IllegalStateException("x") else pure(x)) // whole: throw
  def caught(f: Int -> Int): Int = try f(1) catch case _: RuntimeException => 0 // whole: try
  def partial: Int = ??? // whole: scala.Predef.???
