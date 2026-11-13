//> host: host
package host

import okay.safe.*
import java.net.http.HttpClient

object App extends Entry:
  def run(args: List[String])(using Runner): Unit =
    // whole: the host gives the program's prog.Session.apply what is not data: java.net.http.HttpClient
    val session = prog.Session("ann", HttpClient.newHttpClient())
    // whole: the host gives the program's prog.Greet.hello what is not data: prog.Session.client
    println(okay.safe.run(prog.Greet.hello(session)))
