package okay.safe.example.host

import language.experimental.captureChecking

import okay.safe.*
import okay.safe.std.*
import okay.safe.example.*

/** THE HOST: the edge where a program is run. The client goes to one handler only — made by `Authority`, outside
 * the checks, so the program's type stays pure — and that handler is taken only under the audit. */
object BankHost:
  /** the bank's handler, holding the client: answers each operation in place */
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
    run(Audit.trail(Throws.run(handleAudited(checked, handler[Audit + Throws % String, Long](client)))))

  def main(args: Array[String]): Unit = Runner.host:
    val client = BankClient(Map("company" -> 1000L))
    val (trail, result) = payroll(client, limit = 500, List("ann" -> 300L, "bob" -> 200L))
    trail.foreach(println)
    println(result)
