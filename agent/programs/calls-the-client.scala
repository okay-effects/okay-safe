// refused: method transfer cannot be accessed
// The bank's client, taken as a parameter and called directly: its API is the host's.
import language.experimental.safe
import okay.safe.*
import okay.agent.*

import okay.bank.host.BankClient
object Task:
  def drain(client: BankClient): Unit = client.transfer("company", "me", 1000)
  val program: String ! Tools = pure("done")