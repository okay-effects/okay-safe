// A program in safe mode runs a program inside its continuation. Even given a runner, it cannot name `run`: the
// host's file is neither compiled in safe mode nor `@assumeSafe`.
import language.experimental.safe
import okay.safe.*

object Nested:
  def p(using Runner): Int ! Pure =
    // error: Cannot refer to method run in the top-level definitions in package safe from safe code since it is
    pure[Int, Pure](1).flatMap(x => pure(run(pure[Int, Pure](x))))
