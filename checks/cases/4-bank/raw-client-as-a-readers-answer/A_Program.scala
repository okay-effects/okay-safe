// THE BOUNDARY: the raw client as an effect's answer, through the kernel's own `Reader`: the host runs the program
// with `Reader.run(client)`, and every `ask` is answered with the client. Safe mode does not read a type argument:
// this compiles. The boundary rule refuses the type wherever the program holds it, and a word answered with it.
package prog

import language.experimental.safe
import okay.safe.*
import okay.safe.std.*
import java.net.http.HttpClient

object Use:
  // whole: an effect's answer must be first-order data: java.net.http.HttpClient is not data
  def client: Word[Reader % HttpClient, HttpClient] = Reader.ask[HttpClient]
  // whole: the type java.net.http.HttpClient
  def version[R[_]: Has[Reader % HttpClient]]: String ! R = client.map(c => c.version.toString)
