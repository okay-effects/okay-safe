// refused: BankClient
// The bank's client made by the program: a class of the host's.
import language.experimental.safe
import okay.safe.*
import okay.agent.*

import okay.bank.host.BankClient
object Task:
  val program: String ! Tools = pure(BankClient(Map("company" -> 1L)).toString)