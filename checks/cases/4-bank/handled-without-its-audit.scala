// The bank's handler put where its audit should be, with no audit before it.
import language.experimental.safe
import okay.safe.*
import okay.bank.*

object NoAudit:
  def silent[A](p: A ! Bank, h: Handler[Bank, Pure, A, A]): A ! Pure = handleAudited(p, h) // error: is not in the row
