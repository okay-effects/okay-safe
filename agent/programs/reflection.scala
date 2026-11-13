// refused: Cannot refer to method forName
// Reflection.
import language.experimental.safe
import okay.safe.*
import okay.agent.*

object Task:
  val program: String ! Tools = pure(Class.forName("okay.bank.host.BankHost").getName)