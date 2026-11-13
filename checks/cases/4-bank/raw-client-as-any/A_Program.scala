// THE BOUNDARY: the raw client as `Any`. A program cannot use an `Any` but by a type test or a cast — a cast is
// refused (safe mode), a test against a type it may not hold is refused (the boundary rule) —, but it can still
// reach `toString`, `equals` and `hashCode`, which are the host value's own code: so `Any` is no data at the
// boundary. In a program, of its own values, it is allowed: those are its own.
package prog

import language.experimental.safe
import okay.safe.*

enum Box[X]:
  case Anything() extends Box[Any] // whole: the answer of Anything, an operation of prog.Box, must be
  case Count() extends Box[Int]

object Describe:
  def describe(x: Any): String ! Pure = pure(x.toString)
  def count[R[_]: Has[Box]]: Int ! R = word(Box.Count()).into[R]
  def isClient(x: Any): Boolean = x match
    case _: java.net.http.HttpClient => true // whole: the type java.net.http.HttpClient
    case _ => false
  def own: List[Any] = List(1, "two", 3.0)
