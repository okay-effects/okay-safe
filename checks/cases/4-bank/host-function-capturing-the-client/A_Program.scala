// THE BOUNDARY: a host's function capturing the client, given as data — IO in disguise. The client is a Java
// object, which capture checking does not track: `() -> String` would be believed pure. So no function crosses
// the boundary: not as an argument the host gives, not as an effect's answer — the program's own effect's, or the
// kernel's `State` the host starts with a function. The program's own lambdas are its own.
package prog

import language.experimental.safe
import okay.safe.*
import okay.safe.std.*

enum Lookup[X]:
  case Getter() extends Lookup[() => String] // whole: the answer of Getter, an operation of prog.Lookup, must be
  case Name() extends Lookup[String]

object Fetch:
  def fetch(get: () => String): String ! Pure = pure(get())
  def twice(xs: List[Int]): List[Int] = xs.map(x => x * 2)
  def name[R[_]: Has[Lookup]]: String ! R = word(Lookup.Name()).into[R]
  def peek[R[_]: Has[State % (() -> String)]]: String ! R =
    // whole: an effect's answer must be first-order data: a function
    State.get[() -> String].map(f => f())
