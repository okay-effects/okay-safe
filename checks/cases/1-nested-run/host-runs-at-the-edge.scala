// The positive counterpart: the host runs a program once, at its edge; the program runs nothing.
//> host: host
package host

import language.experimental.captureChecking
import okay.safe.*
import okay.safe.std.*

object Edge:
  def main(args: Array[String]): Unit =
    val p: Int ! (State % Int) = State.get[Int].flatMap(s => State.put(s + 1).map(_ => s))
    val (s, a) = Runner.host(run(State.run(41)(p)))
    assert(s == 42 && a == 41)
