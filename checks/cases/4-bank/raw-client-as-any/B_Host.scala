//> host: host
package host

import okay.safe.*
import java.net.http.HttpClient

object App extends Entry:
  def run(args: List[String])(using Runner): Unit =
    val client = HttpClient.newHttpClient()
    // whole: the host gives the program's prog.Describe.describe what is not data: java.net.http.HttpClient
    println(okay.safe.run(prog.Describe.describe(client)))
    println(okay.safe.run(prog.Describe.describe(42)))
