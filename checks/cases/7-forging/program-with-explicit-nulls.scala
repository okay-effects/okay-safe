// The positive counterpart: compiled with explicit nulls, where a null is said in the type.
//> flags: -Yexplicit-nulls
//> require: explicit-nulls
import language.experimental.safe

object Nullable:
  val s: String | Null = null
