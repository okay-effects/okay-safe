// A global object that is `Mutable`: every use of it, from a method or a pure function, must be declared — the
// object's own uses clause (`uses Counter`); undeclared, it does not compile.
package global
import language.experimental.safe

object Counter extends caps.Mutable:
  var n = 0
  update def bump(): Unit = n += 1

object Use:
  def eager(): Int = { Counter.bump(); Counter.n } // error: Reference `global.Counter` is not included in the allowed capture set {}
  val f: Int -> Int = x => { Counter.bump(); x } // error: Reference `global.Counter` is not included in the allowed capture set {}
