//> host: host
package host

import java.net.http.HttpClient

object App:
  def main(args: Array[String]): Unit =
    println(prog.Show.show(HttpClient.newHttpClient()))
