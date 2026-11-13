// The positive counterpart: the application of `example` — the payroll in safe mode, the audit, the client held by
// one handler at the host's edge (RuntimeTest runs it and counts what reached the client).
//> host: host
package host

import language.experimental.captureChecking
import okay.safe.*
import okay.safe.example.host.{BankClient, BankHost}

object App extends Entry:
  def run(args: List[String])(using Runner): Unit =
    val client = BankClient(Map("company" -> 100L))
    val (_, result) = BankHost.payroll(client, 50, List("ann" -> 40L, "bob" -> 70L))
    assert(result == Left("over the limit: 70"))
