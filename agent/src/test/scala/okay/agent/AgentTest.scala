package okay.agent

import java.nio.file.{Files, Path}

import scala.jdk.CollectionConverters.*

import okay.safe.{!, hosted}
import okay.agent.host.{AgentGate, Rules}
import okay.bank.host.{BankClient, BankHost}

/** every program of agent/programs through the gate: `// runs: <answer>` passes it and runs to that answer under
 * the default rules; `// refused: <reason>` is refused with that reason. The time each took is printed. */
class AgentTest extends munit.FunSuite:
  val dir: Path = Path.of(sys.props("okay.agent.programs"))
  val programs: List[Path] =
    Files.list(dir).iterator.asScala.filter(_.toString.endsWith(".scala")).toList.sortBy(_.toString)
  val Expect = """//\s*(runs|refused):\s*(.+)""".r

  def gated(file: Path) =
    val outcome = AgentGate.gate(file, AgentGate.classpath)
    val (c, w) = outcome.fold(r => (r.compileMs, r.checkMs), p => (p.compileMs, p.checkMs))
    println(f"${file.getFileName}%-24s compiled in $c%5d ms, checked in $w%4d ms")
    outcome

  def ran(program: String ! Tools, approve: Boolean = false,
      client: BankClient = BankClient(Map("company" -> 1000L)))
      : AgentGate.Outcome =
    hosted(AgentGate.run(program, client, Rules.decide(Rules.default, (_, _) => approve)))

  for file <- programs do
    test(file.getFileName.toString):
      Files.readAllLines(file).asScala.headOption match
        case Some(Expect("runs", answer)) =>
          val passed = gated(file).fold(r => fail(s"refused:\n${r.reasons.mkString("\n")}"), identity)
          assertEquals(ran(passed.program).result.toString, answer)
        case Some(Expect("refused", why)) =>
          val refused = gated(file).fold(identity, _ => fail("passed the gate"))
          assert(refused.reasons.exists(_.toString.contains(why)), s"$why not in\n${refused.reasons.mkString("\n")}")
        case other => fail(s"no `// runs:` or `// refused:` on the first line: $other")

  test("the sensitive operation passes the policy: logged, audited, and only what passed reached the bank"):
    val passed = gated(dir.resolve("good.scala")).fold(r => fail(r.reasons.mkString("\n")), identity)
    val client = BankClient(Map("company" -> 1000L))
    val outcome = ran(passed.program, client = client)
    assertEquals(outcome.log, List("the company holds 1000"))
    assertEquals(outcome.trail(1), "passed: bank.transfer Map(from -> company, to -> ann, amount -> 300)")
    assertEquals((BankHost.calls(client), BankHost.holds(client, "ann")), (3, 300L))

  test("blocked by the policy: the bank never asked; asked and approved: passed"):
    val blocked = gated(dir.resolve("new-payee.scala")).fold(r => fail(r.reasons.mkString("\n")), identity)
    val client = BankClient(Map("company" -> 1000L))
    assertEquals(ran(blocked.program, client = client).trail, List("blocked: bank.transfer " +
      "Map(from -> company, to -> mallory, amount -> 10): refused: a new payee"))
    assertEquals(BankHost.calls(client), 0)
    assertEquals(ran(blocked.program, approve = true).result, Right("paid mallory 10"))
