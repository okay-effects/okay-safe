//> host: host
package host

import okay.safe.*
import okay.safe.std.*
import java.net.http.HttpClient

object App extends Entry:
  def run(args: List[String])(using Runner): Unit =
    val client = HttpClient.newHttpClient()
    println(okay.safe.run(Reader.run(client)(prog.Use.version[Reader % HttpClient])))
