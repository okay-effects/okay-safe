// The same client, called from a program's continuation, so that the call would happen inside the run, past the
// handler and its audit: the continuation is a pure function, and would capture the client.
import language.experimental.safe
import okay.safe.*
import okay.safe.example.host.BankClient

object Inside:
  def p(client: BankClient): Unit ! Pure =
    pure[Int, Pure](1).flatMap(x => pure(client.transfer("company", "me", x))) // error: Note that capability `client` cannot flow into capture set {}
