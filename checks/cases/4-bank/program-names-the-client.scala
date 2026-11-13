// A program in safe mode given the bank's client calls it itself. Safe mode checks the static references only — a
// method of a parameter it takes as checked —, so this compiles: the client is in the program's signature, for all
// to see. The whole-program check refuses it: the client's package is the host's.
//> host: okay.safe.example.host
import language.experimental.safe
import okay.safe.*
import okay.safe.example.host.BankClient

object Direct:
  def drain(client: BankClient): Unit = client.transfer("company", "me", 1000) // whole: a program names the host
