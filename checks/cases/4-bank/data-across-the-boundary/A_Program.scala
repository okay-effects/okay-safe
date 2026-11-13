// THE BOUNDARY, positive: what a host may give a program and a handler may answer — primitives, strings, numbers,
// options, eithers, tuples, immutable collections, an instant, the program's own final classes and enums of data —
// and what a program may do with its own lambdas: anything.
package prog

import language.experimental.safe
import okay.safe.*

final case class Payee(name: String, account: String)
enum Kind:
  case Salary, Bonus
final case class Order(to: Payee, amount: BigDecimal, kind: Kind, note: Option[String], at: java.time.Instant)

enum Ledger[X]:
  case Last() extends Ledger[Option[Order]]
  case Totals() extends Ledger[Map[String, (Long, List[BigInt])]]
  case Check(order: Order) extends Ledger[Either[String, Unit]]

object Pay:
  def last: Word[Ledger, Option[Order]] = word(Ledger.Last())
  def plan(orders: List[Order], limits: Map[String, Long], tag: (Int, String)): Vector[String] =
    val big: Order => Boolean = o => o.amount > BigDecimal(limits.getOrElse(o.to.name, 0L))
    orders.filter(big).map(o => s"${tag._2}: ${o.to.name}").toVector
  def run[R[_]: Has[Ledger]]: String ! R = last.map(o => o.fold("none")(_.to.name))
