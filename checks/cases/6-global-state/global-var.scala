// A global variable: capture checking asks a class with a variable to be `Stateful` or `Mutable`.
import language.experimental.safe

object Counter:
  var n = 0 // error: Mutable variable n is defined in a class that does not extend `Stateful` or `Mutable`
