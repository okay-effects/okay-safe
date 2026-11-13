// A program compiled without explicit nulls, where the policy asks them: the whole-program check refuses it.
//> require: explicit-nulls
import language.experimental.safe // whole: not compiled with explicit nulls

object Nullable:
  val s: String = null
