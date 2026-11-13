// The README's host, as written there.
//> host: readme.host
package readme.host

import language.experimental.captureChecking
import okay.safe.*
import okay.safe.std.*
import readme.tick

@main def app(): Unit = Runner.host:
  println(run(State.run(0)(tick[State % Int])))
