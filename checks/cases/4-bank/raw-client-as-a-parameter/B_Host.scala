//> host: host
package host

import okay.safe.*
import java.net.http.HttpClient
import okay.bank.host.{BankClient, BankHost, PaymentService}

/** the bank's host, mistaken: the client its Bank handler holds, given to a program too */
object App extends Entry:
  def run(args: List[String])(using Runner): Unit =
    val service = PaymentService.start(Map("company" -> 1000L))
    val http = HttpClient.newHttpClient()
    println(BankHost.payroll(BankClient(http, service.uri), 500, List("ann" -> 300L)))
    // whole: the host gives the program's prog.Report.status what is not data: java.net.http.HttpClient
    println(okay.safe.run(prog.Report.status(http)))
