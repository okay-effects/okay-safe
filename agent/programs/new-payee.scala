// runs: Left(blocked: bank.transfer: refused: a new payee)
// A transfer to a new payee: the guard asks; no one approves here, so it is blocked (AgentTest approves it too).
import language.experimental.safe
import okay.safe.*
import okay.bank.Bank
import okay.agent.*

object Task:
  val program: String ! Tools = Bank.transfer("company", "mallory", 10).map(_ => "paid mallory 10")