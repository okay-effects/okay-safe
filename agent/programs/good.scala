// runs: Right(paid ann 300; left 700)
// An agent's program: reads the balance, pays within the limit, says what it did. The transfer passes the guard.
import language.experimental.safe
import okay.safe.*
import okay.bank.Bank
import okay.agent.*

object Task:
  val program: String ! Tools =
    for
      before <- Bank.balance("company")
      _ <- Log.line(s"the company holds $before")
      _ <- Bank.transfer("company", "ann", 300)
      after <- Bank.balance("company")
    yield s"paid ann 300; left $after"
