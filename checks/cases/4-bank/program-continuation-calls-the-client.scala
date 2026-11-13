// The same client, called from a program's continuation, so that the call would happen inside the run, past the
// handler and its audit: refused twice over — the API is the host's (`private[host]`), and the continuation is a
// pure function, which would capture the client (host-continuation-calls-the-client shows that alone).
import language.experimental.safe
import okay.safe.*
import okay.safe.example.host.BankClient

object Inside:
  def p(client: BankClient): Unit ! Pure =
    // error: method transfer cannot be accessed as a member of
    pure[Int, Pure](1).flatMap(x => pure(client.transfer("company", "me", x)))
