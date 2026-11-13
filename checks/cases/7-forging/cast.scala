// A cast: what a sensitive effect's audit, or a capability, could be forged by.
import language.experimental.safe
import okay.safe.*
import okay.bank.*

object Cast:
  def plain: Plain[Bank] = ().asInstanceOf[Plain[Bank]] // error: Cannot use asInstanceOf in safe mode
