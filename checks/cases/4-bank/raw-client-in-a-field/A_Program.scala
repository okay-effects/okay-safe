// THE BOUNDARY: the raw client as a field of the program's own case class — a session that carries its connection.
package prog

import language.experimental.safe
import okay.safe.*
import java.net.http.HttpClient

final case class Session(user: String, client: HttpClient) // whole: the type java.net.http.HttpClient

object Greet:
  def hello(s: Session): String ! Pure = pure(s"hello ${s.user}")
