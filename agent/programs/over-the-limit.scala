// runs: Left(blocked: bank.transfer: over the limit)
// A transfer above the limit: the program compiles and passes the gate; the guard blocks the transfer.
import language.experimental.safe
import okay.safe.*
import okay.bank.Bank
import okay.agent.*

object Task:
  val program: String ! Tools = Bank.transfer("company", "bob", 900).map(_ => "paid bob 900")