//> host: host
package host

import okay.safe.*
import okay.safe.std.*
import java.net.http.HttpClient

object App extends Entry:
  def run(args: List[String])(using Runner): Unit =
    val client = HttpClient.newHttpClient()
    // whole: the host gives the program's prog.Fetch.fetch what is not data: a function
    println(okay.safe.run(prog.Fetch.fetch(() => client.version.toString)))
    println(prog.Fetch.twice(List(1, 2)))
    println(okay.safe.run(State.run[() => String, State % (() => String), Pure, String](() => client.version.toString)(
      prog.Fetch.peek[State % (() => String)])))
