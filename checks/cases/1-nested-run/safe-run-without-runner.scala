// The same with no runner in scope: the typer refuses first — a run needs a `Runner`, which only the host makes.
import language.experimental.safe
import okay.safe.*

object Nested:
  // error: No given instance of type okay.safe.Runner
  val p: Int ! Pure = pure[Int, Pure](1).flatMap(x => pure(run(pure[Int, Pure](x))))
