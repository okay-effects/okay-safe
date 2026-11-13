// THE BOUNDARY: a class of the program that is neither final nor sealed. A host may subclass it — override its
// `toString`, its methods — and give the subclass where the class is asked: its code runs in the program. Data at
// the boundary is final or sealed.
package prog

import language.experimental.safe
import okay.safe.*

case class Account(name: String)
final case class Closed(name: String)

object Show:
  def show(a: Account): String ! Pure = pure(a.toString)
  def shut(a: Closed): String ! Pure = pure(a.name)
