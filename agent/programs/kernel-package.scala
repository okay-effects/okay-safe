// refused: declares into the kernel's package okay.bank.host
// A unit declared into the bank's host package, where the client's API is open: it compiles — package-private
// access is Scala's —, and the whole-program check refuses any unit there.
import language.experimental.safe
import okay.safe.*
import okay.agent.*

package okay.bank.host {
  object Sneak:
    def drain(client: BankClient): Unit = client.transfer("company", "me", 1000)
}

object Task:
  val program: String ! Tools = pure("done")
