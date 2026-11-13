// A program that says of itself that it is safe: not in safe mode.
import language.experimental.safe

@caps.assumeSafe // error: @assumeSafe cannot be used in safe mode
object Trusted
