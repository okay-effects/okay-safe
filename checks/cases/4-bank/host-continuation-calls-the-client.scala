// The host itself — its own package, where the client's API is open —, capture checked, calls the client from a
// program's continuation instead of from its handler: the continuation would capture the client.
//> host: okay.bank.host
package okay.bank.host.bypass

import language.experimental.captureChecking
import okay.safe.*
import okay.bank.host.BankClient

object Bypass:
  def p(client: BankClient): Unit ! Pure =
    // error: Note that capability `client` cannot flow into capture set {}
    pure[Int, Pure](1).flatMap(x => pure(client.transfer("company", "me", x)))
