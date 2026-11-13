//> host: host
package host

import okay.safe.*

object App extends Entry:
  def run(args: List[String])(using Runner): Unit =
    val order = prog.Order(prog.Payee("ann", "1"), BigDecimal(10), prog.Kind.Salary, None, java.time.Instant.EPOCH)
    println(prog.Pay.plan(List(order), Map("ann" -> 5L), (1, "big")))
