// A program in safe mode given the bank's client calls it itself. Safe mode checks the static references only — a
// method of a parameter it takes as checked —, so this compiled once (the whole-program check refused it). Now the
// client's API is the host's own: every member `private[host]`, so the typer refuses the call. The client is a
// capability nobody but the host's package can use; the host's package calls it from one handler.
import language.experimental.safe
import okay.safe.*
import okay.bank.host.BankClient

object Direct:
  // error: method transfer cannot be accessed as a member of
  def drain(client: BankClient): Unit = client.transfer("company", "me", 1000)
  // error: method balance cannot be accessed as a member of
  def peek(client: BankClient): Long = client.balance("company")
