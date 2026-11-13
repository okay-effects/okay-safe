// The README's program, as written there.
package readme

import language.experimental.safe
import language.experimental.separationChecking
import okay.safe.*
import okay.safe.std.*

def tick[R[_]: Has[State % Int]]: Int ! R = State.get[Int].flatMap(n => State.put(n + 1).map(_ => n))
