//> host: host
package host

import okay.safe.*
import java.net.http.HttpClient

object App extends Entry:
  def run(args: List[String])(using Runner): Unit =
    val client = HttpClient.newHttpClient()
    val asks: Int ! Pure = pure[Unit, Pure](()).map(_ => client.version.ordinal)
    // whole: the host gives the program's prog.Twice.twice what is not data: okay.safe.Comp
    println(okay.safe.run(prog.Twice.twice(asks)))
