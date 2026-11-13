// The positive counterpart: the host's entry is given the runner once, and runs a program at its edge; the program
// runs nothing.
//> host: host
package host

import language.experimental.captureChecking
import okay.safe.*
import okay.safe.std.*

object Edge extends Entry:
  def run(args: List[String])(using Runner): Unit =
    val p: Int ! (State % Int) = State.get[Int].flatMap(s => State.put(s + 1).map(_ => s))
    val (s, a) = okay.safe.run(State.run(41)(p))
    assert(s == 42 && a == 41)
