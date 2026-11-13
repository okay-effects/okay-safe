// A program handles the sensitive effect itself, past its audit.
import language.experimental.safe
import okay.safe.*
import okay.bank.*

object Unaudited:
  def silent[A](p: A ! Bank): A ! Pure =
    handle(p, Handler.tail[Bank, Pure, A, A](pure)([X] => (op: Bank[X]) => op match
      case Bank.Balance(_) => 1_000_000L
      case Bank.Transfer(_, _, _) => ())) // error: the effect Bank is sensitive: it is handled only under its audit
