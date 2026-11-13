// The host, capture checked, calls the client from a program's continuation instead of from its handler: the
// continuation would capture the client.
//> host: host
package host

import language.experimental.captureChecking
import okay.safe.*
import okay.safe.example.host.BankClient

object Bypass:
  def p(client: BankClient): Unit ! Pure =
    pure[Int, Pure](1).flatMap(x => pure(client.transfer("company", "me", x))) // error: Note that capability `client` cannot flow into capture set {}
