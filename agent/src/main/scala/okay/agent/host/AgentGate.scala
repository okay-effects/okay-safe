package okay.agent.host

import java.io.File
import java.net.URLClassLoader
import java.nio.file.{Files, Path}

import okay.safe.*
import okay.safe.std.*
import okay.safe.gate.Gate
import okay.safe.whole.Policy
import okay.agent.*
import okay.bank.Bank
import okay.bank.host.{BankClient, BankHost}

/*
 * THE GATE FOR AGENT-WRITTEN CODE. An agent writes a program over the tools it is given:
 *
 *   object Task:
 *     val program: String ! Tools = ...
 *
 * in safe mode, in the empty package. The gate compiles it with the required flags, beside a file of its own that
 * fixes the program's type (`GateEntry.program: String ! Tools`), holds both to the whole-program check — every
 * unit a program, none allowed into the kernel's, the bank's or the agent's packages, nothing of the host named —
 * and only then loads it and runs it with the host's handlers: the log kept, the system's clock, the bank's client
 * behind its audit, and the audit asking the guard, whose handler is the policy. Host code: not capture checked.
 */
object AgentGate:
  /** the policy for agents' programs: the default, the bank's and the agent's API allowed, their hosts not */
  val policy: Policy = Policy.default.copy(kernel = Policy.default.kernel ++ List("okay.agent", "okay.bank"),
    host = List("okay.agent.host", "okay.bank.host"))

  /** the gate's own file: the program's type, fixed by the compiler */
  val entry: String =
    """import language.experimental.safe
      |import okay.safe.*
      |
      |object GateEntry:
      |  val program: String ! okay.agent.Tools = Task.program
      |""".stripMargin

  /** a program that passed: it, and how long the compiler and the check took */
  final case class Passed(program: String ! Tools, compileMs: Long, checkMs: Long)

  /** a program refused: why, and how long it took to say so */
  final case class Refused(reasons: List[Gate.Reason], compileMs: Long, checkMs: Long)

  /** the source compiled and checked against `classpath` (the kernel, the bank, the agent's API, the library) */
  def gate(source: Path, classpath: List[Path]): Either[Refused, Passed] =
    val dir = Files.createTempDirectory("okay-agent")
    val out = dir.resolve("classes")
    val wrapper = Files.writeString(dir.resolve("GateEntry.scala"), entry)
    def ms(start: Long) = (System.nanoTime() - start) / 1_000_000
    val compiling = System.nanoTime()
    val errors = Gate.compile(List(source, wrapper), classpath, out)
    val compileMs = ms(compiling)
    if errors.nonEmpty then Left(Refused(errors, compileMs, 0))
    else
      val checking = System.nanoTime()
      val findings = Gate.check(out, classpath, policy)
      val checkMs = ms(checking)
      if findings.nonEmpty then Left(Refused(findings, compileMs, checkMs))
      else Right(Passed(load(out), compileMs, checkMs))

  /** the program, loaded from what passed the gate: its type is the one the compiler fixed in GateEntry */
  private def load(out: Path): String ! Tools =
    val loader = URLClassLoader(Array(out.toUri.toURL), getClass.getClassLoader)
    val gateEntry = Class.forName("GateEntry$", true, loader)
    gateEntry.getMethod("program").invoke(gateEntry.getField("MODULE$").get(null)) match
      case p: Comp[?, ?] => p.asInstanceOf[String ! Tools]
      case other => sys.error(s"GateEntry.program is no program: $other")

  /** what a run came to: the audit's trail, the program's log, its answer or the failure that ended it */
  final case class Outcome(trail: List[String], log: List[String], result: Either[String, String])

  type Outside[X] = (Guard + Audit + Throws % String + Log + Clock + Audited[Bank])[X]
  type Handled[X] = (Guard + Audit + Throws % String + Log + Clock)[X]

  /** the program run with the host's handlers: the bank's client behind the guard's audit, the policy `decide` */
  def run(program: String ! Tools, client: BankClient, decide: Call => Decision)(using Runner): Outcome =
    val body: String ! (Outside + Bank) = program.into[Outside + Bank]
    val audited: String ! Outside = audit(Guard.bank[Outside, String])(body)
    val banked: String ! Handled = handleAudited(audited, BankHost.handler[Handled, String](client))
    val clocked = Authority.systemClock[Handled, Guard + Audit + Throws % String + Log, String](banked)
    val failed = Throws.run(clocked)
    val logged = Log.lines(failed)
    val trailed = Audit.trail(logged)
    type Ran = (List[String], (List[String], Either[String, String]))
    val guarded = handle(trailed, Authority.tail[Guard, Pure, Ran, Ran](pure)([X] => (op: Guard[X]) => op match
      case Guard.Decide(call) => decide(call)))
    val (trail, (log, result)) = okay.safe.run(guarded)
    Outcome(trail, log, result)

  /** the class path of this JVM: what an agent's program is compiled and checked against */
  def classpath: List[Path] =
    sys.props("java.class.path").split(File.pathSeparator).toList.filter(_.nonEmpty).map(Path.of(_))
