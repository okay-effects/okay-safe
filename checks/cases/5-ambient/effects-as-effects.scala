// The positive counterpart: the time and fresh numbers asked as effects; the host answers them (Authority.systemClock).
import language.experimental.safe
import okay.safe.*
import okay.safe.std.*

object Asked:
  def stamped[R[_]: Has[Clock] : Has[Fresh]]: (Long, Long) ! R =
    for t <- Clock.now; id <- Fresh.next yield (t, id)
  val fixed: (Long, Long) ! Pure = Fresh.run(Clock.at(() => 1000L)(stamped[Pure + Fresh + Clock]))
