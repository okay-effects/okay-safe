// refused: the effect Bank is sensitive
// The bank handled by the program itself, past its audit and the guard.
import language.experimental.safe
import okay.safe.*
import okay.bank.Bank
import okay.safe.std.Clock
import okay.agent.*

object Task:
  val fake: String ! Tools = Bank.transfer("company", "me", 1000).map(_ => "rich")
  val program: String ! Tools = handle(fake, Handler.tail[Bank, Log + Clock, String, String](pure)(
    [X] => (op: Bank[X]) => op match
      case Bank.Balance(_) => 0L
      case Bank.Transfer(_, _, _) => ())).into[Tools]