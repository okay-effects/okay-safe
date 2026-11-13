// The positive counterpart: the application of `example` — the payroll in safe mode, the audit, the client held by
// one handler at the host's edge, over HTTP to the payment service (RuntimeTest runs it and reads what the service
// received).
//> host: host
package host

import language.experimental.captureChecking
import java.net.http.HttpClient
import okay.safe.*
import okay.bank.host.{BankClient, BankHost, PaymentService}

object App extends Entry:
  def run(args: List[String])(using Runner): Unit =
    val service = PaymentService.start(Map("company" -> 100L))
    val (_, result) =
      BankHost.payroll(BankClient(HttpClient.newHttpClient(), service.uri), 50, List("ann" -> 40L, "bob" -> 70L))
    assert(result == Left("over the limit: 70"))
    service.stop()
