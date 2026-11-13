// refused: Cannot use asInstanceOf in safe mode
// A cast: forges any type.
import language.experimental.safe
import okay.safe.*
import okay.agent.*

object Task:
  val program: String ! Tools = pure[Any, Tools]("x").map(_.asInstanceOf[String])