// `@unchecked` on a scrutinee: tagged @rejectSafe, yet safe mode does not see it there. It only silences a warning
// (here: a match that is not exhaustive), but under -Werror a silenced warning is a check undone. The
// whole-program check refuses it.
import language.experimental.safe

object Matched:
  def matched(x: Option[Int]): Int = (x: @unchecked) match // whole: scala.unchecked
    case Some(i) => i
