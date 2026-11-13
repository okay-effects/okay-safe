// The README's host, as written there.
//> host: readme.host
package readme.host

import language.experimental.captureChecking
import okay.safe.*
import okay.safe.std.*
import readme.tick

object App extends Entry:
  def run(args: List[String])(using Runner): Unit =
    println(okay.safe.run(State.run(0)(tick[State % Int])))
