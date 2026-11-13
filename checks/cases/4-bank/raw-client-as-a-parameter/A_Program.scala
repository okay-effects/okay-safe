// THE BOUNDARY: a host's mistake — the raw HTTP client given to a program as a parameter. Safe mode does not check
// a parameter's type, and takes its instance members as checked: this compiles. The boundary rule refuses the type
// in the program, and the call in the host.
package prog

import language.experimental.safe
import okay.safe.*
import java.net.http.HttpClient

object Report:
  def status(client: HttpClient): String ! Pure = pure(s"connected: $client") // whole: java.net.http.HttpClient
