// An id forged by a cast: safe mode refuses it. (table-given-to-a-program: the table that holds the resources.)
import language.experimental.safe
import okay.safe.*

object Forge:
  // error: Cannot use asInstanceOf in safe mode
  def cast: Id[List] = 1L.asInstanceOf[Id[List]]
