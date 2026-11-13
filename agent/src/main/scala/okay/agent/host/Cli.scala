package okay.agent.host

import java.net.http.HttpClient
import java.nio.file.{Files, Path}

import okay.safe.{Entry, Runner}
import okay.bank.host.{BankClient, PaymentService}

/** `okay-agent <Task.scala> [--rules <file>]`: the agent's program through the gate — refused with its reasons
 * (exit 1), or run against a payment service on the loopback, over HTTP, under the rules (Rules.default without
 * `--rules`): its log, the audit's trail, its answer, and what the service received. Started by the kernel's
 * Launch, its entry this (build.sbt: `sbt "agent/run <file>"`). */
object Cli extends Entry:
  def run(args: List[String])(using Runner): Unit =
    val source = args.headOption.map(Path.of(_)).getOrElse(sys.error("usage: <Task.scala> [--rules <file>]"))
    val rules = args.sliding(2).collectFirst { case List("--rules", f) => f }
      .map(f => Rules.read(Files.readString(Path.of(f))).fold(sys.error, identity)).getOrElse(Rules.default)
    AgentGate.gate(source, AgentGate.classpath) match
      case Left(refused) =>
        println(s"REFUSED (compiled in ${refused.compileMs} ms, checked in ${refused.checkMs} ms):")
        refused.reasons.foreach(r => println(s"  $r"))
        sys.exit(1)
      case Right(passed) =>
        println(s"passed the gate (compiled in ${passed.compileMs} ms, checked in ${passed.checkMs} ms)")
        val service = PaymentService.start(Map("company" -> 1000L))
        val http = HttpClient.newHttpClient()
        val client = BankClient(http, service.uri)
        val outcome = AgentGate.run(passed.program, client, Rules.decide(rules, (_, _) => false))
        outcome.log.foreach(l => println(s"log: $l"))
        outcome.trail.foreach(l => println(s"audit: $l"))
        println(s"result: ${outcome.result}")
        service.received.foreach(r => println(s"the bank received: $r"))
        http.close()
        service.stop()
