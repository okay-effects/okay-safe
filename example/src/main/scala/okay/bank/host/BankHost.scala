package okay.bank.host

import language.experimental.captureChecking

import java.net.http.HttpClient

import okay.safe.*
import okay.safe.std.*
import okay.bank.*

/** THE HOST: the edge where a program is run. The client goes to one handler only — made by `Authority`, outside
 * the checks, so the program's type stays pure — and that handler is taken only under the audit. Its entry is
 * `run`, given the runner once by `Launch`. The real resource — an `HttpClient` to the payment service — is made
 * here and held by the handler alone: a program gets data (accounts, amounts) and effects, never the client. */
object BankHost extends Entry:
  /** the bank's handler, holding the client: answers each operation in place, by a request to the service */
  def handler[R[_], A](client: BankClient): Handler[Bank, R, A, A] =
    Authority.tail[Bank, R, A, A](pure)([X] => (op: Bank[X]) => op match
      case Bank.Balance(account) => client.balance(account)
      case Bank.Transfer(from, to, amount) => client.transfer(from, to, amount))

  type Outside[X] = (Audit + Throws % String + Audited[Bank])[X]

  /** the payroll run against the client, audited with `limit`: the trail and the result */
  def payroll(client: BankClient, limit: Long, staff: List[(String, Long)])(using Runner)
      : (List[String], Either[String, Long]) =
    val program = Payroll.pay[Outside + Bank]("company", staff)
    val checked = audit(Payroll.audited[Outside, Long](limit))(program)
    okay.safe.run(Audit.trail(Throws.run(handleAudited(checked, handler[Audit + Throws % String, Long](client)))))

  def run(args: List[String])(using Runner): Unit =
    val service = PaymentService.start(Map("company" -> 1000L))
    val http = HttpClient.newHttpClient()
    val (trail, result) = payroll(BankClient(http, service.uri), limit = 500, List("ann" -> 300L, "bob" -> 200L))
    trail.foreach(println)
    println(result)
    service.received.foreach(r => println(s"the service received: $r"))
    http.close()
    service.stop()
