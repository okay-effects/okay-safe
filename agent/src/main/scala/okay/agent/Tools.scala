package okay.agent

import language.experimental.safe
import language.experimental.separationChecking

import okay.safe.*
import okay.safe.std.*
import okay.bank.Bank

/*
 * WHAT AN AGENT'S PROGRAM MAY ASK, AND THE AUDIT ITS SENSITIVE ASKS PASS. Compiled in safe mode: the program side.
 * An agent writes `object Task: val program: String ! Tools = ...` over these effects; the gate (host/AgentGate)
 * compiles it in safe mode, checks the whole of it, and only then runs it with the host's handlers. The bank is
 * sensitive: every operation of it goes to the GUARD first — an effect, so the policy is a handler the host
 * chooses: rules here (host/Rules.scala), okay-guard's rules or its person-in-the-loop over the wire there.
 */

/** a line the program says, for its user */
enum Log[X]:
  case Line(text: String) extends Log[Unit]

object Log:
  def line(text: String): Word[Log, Unit] = word(Line(text))

  /** the lines said, in order, with the value */
  def lines[R[_], Rest[_], A](p: A ! R)(using Split[Log, R, Rest]): (List[String], A) ! Rest =
    handle(p, Handler.stateful[Log, Rest, A, (List[String], A), List[String]](Nil)((lines, a) =>
      pure((lines.reverse, a)))([X] => (op: Log[X], lines: Cell[List[String]]^) => op match
        case Line(text) => lines.set(text :: lines.get)))

/** THE TOOLS of an agent's program: a log, the clock, the bank — the bank last, where its audit takes it */
type Tools[X] = (Log + Clock + Bank)[X]

/** A CALL as okay-guard sees one: a tool's name and its arguments */
final case class Call(tool: String, arguments: Map[String, String])

/** what a policy decided of a call: passed, or blocked with the reason. (okay-guard's `ask` is the guard's own: its
 * handler asks a person and answers one of these.) */
enum Decision:
  case Pass
  case Block(reason: String)

/** THE GUARD: a policy asked of each sensitive call */
enum Guard[X]:
  case Decide(call: Call) extends Guard[Decision]

object Guard:
  def decide(call: Call): Word[Guard, Decision] = word(Decide(call))

  /** a bank's operation as a call */
  def call[X](op: Bank[X]): Call = op match
    case Bank.Balance(account) => Call("bank.balance", Map("account" -> account))
    case Bank.Transfer(from, to, amount) =>
      Call("bank.transfer", Map("from" -> from, "to" -> to, "amount" -> amount.toString))

  /** THE BANK'S AUDIT BY THE GUARD: each operation recorded, decided; passed on to the bank, or refused — recorded,
   * and the program failed with the reason before the bank is asked */
  def bank[T[_]: Has[Guard] : Has[Audit] : Has[Throws % String], A]: Interceptor[Bank, T, A, A] =
    new Interceptor[Bank, T, A, A]:
      def ret(a: A): A ! T = pure(a)
      def apply[X](op: Bank[X], k: Continuation[X, T, A], pass: Pass[Bank, T]): A ! T =
        val c = call(op)
        Guard.decide(c).flatMap {
          case Decision.Pass =>
            for
              _ <- Audit.record(s"passed: ${c.tool} ${c.arguments}")
              answer <- k.after(pass(op))
            yield answer
          case Decision.Block(reason) =>
            val refused: A ! T = Throws.raise[String](s"blocked: ${c.tool}: $reason").into[T]
            Audit.record(s"blocked: ${c.tool} ${c.arguments}: $reason").flatMap(_ => refused)
        }
