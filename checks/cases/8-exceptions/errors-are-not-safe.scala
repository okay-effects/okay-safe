// `java.lang.Error` and its subclasses are not assumed safe — yet a type pattern is a type, and safe mode checks
// the terms only: catching a stack overflow compiles. The whole-program check refuses the `try`.
import language.experimental.safe

object Errors:
  def overflow(f: () -> Int): Int = try f() catch case _: StackOverflowError => 0 // whole: try
