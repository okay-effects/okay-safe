package okay.agent.host

import okay.agent.{Call, Decision}

/*
 * A POLICY IN OKAY-GUARD'S SHAPE: rules in order, the first that matches decides — pass, ask, block — for a tool
 * or a prefix (`bank.*`), with an optional condition on an argument (`amount > 500`, `to contains acme`). One a line:
 *
 *   block bank.transfer amount > 500 : over the limit
 *   ask bank.transfer to == mallory : a new payee
 *   pass bank.*
 *
 * None matches: the call passes, as in okay-guard. `ask` is answered by the host's `ask` (a person; none here: it
 * blocks). This is the guard's handler at the host; okay-guard's own — its rules, its page, its journal — would be
 * another handler of the same `Guard` effect, answering over its wire.
 */
enum Action:
  case Pass, Ask, Block

final case class Cond(path: String, op: String, value: String):
  def holds(arguments: Map[String, String]): Boolean = arguments.get(path).exists { v =>
    def compare: Int = (v.toLongOption, value.toLongOption) match
      case (Some(a), Some(b)) => a.compare(b)
      case _ => v.compareTo(value)
    op match
      case "==" => v == value
      case "!=" => v != value
      case "contains" => v.contains(value)
      case ">" => compare > 0
      case ">=" => compare >= 0
      case "<" => compare < 0
      case "<=" => compare <= 0
      case _ => false
  }

final case class Rule(tool: String, when: Option[Cond], action: Action, reason: String):
  def matches(call: Call): Boolean =
    (if tool.endsWith("*") then call.tool.startsWith(tool.dropRight(1)) else call.tool == tool) &&
      when.forall(_.holds(call.arguments))

object Rules:
  /** the first rule that matches decides; none: the call passes */
  def decide(rules: List[Rule], ask: (Call, String) => Boolean)(call: Call): Decision =
    rules.find(_.matches(call)) match
      case None => Decision.Pass
      case Some(r) => r.action match
        case Action.Pass => Decision.Pass
        case Action.Block => Decision.Block(r.reason)
        case Action.Ask => if ask(call, r.reason) then Decision.Pass else Decision.Block(s"refused: ${r.reason}")

  /** `<pass|ask|block> <tool> [<argument> <op> <value>] [: <reason>]` */
  def parse(line: String): Either[String, Rule] =
    val (head, reason) = line.split(" : ", 2) match
      case Array(h, r) => (h.trim, r.trim)
      case Array(h) => (h.trim, "")
    val action = (w: String) => Action.values.find(_.toString.equalsIgnoreCase(w)).toRight(s"pass, ask or block: $w")
    head.split("\\s+").toList match
      case List(a, tool) => action(a).map(Rule(tool, None, _, reason))
      case List(a, tool, path, op, value) => action(a).map(Rule(tool, Some(Cond(path, op, value)), _, reason))
      case _ => Left(s"a rule is `<action> <tool> [<argument> <op> <value>] [: <reason>]`: $line")

  def read(text: String): Either[String, List[Rule]] =
    text.linesIterator.map(_.trim).filter(l => l.nonEmpty && !l.startsWith("#")).toList
      .foldRight[Either[String, List[Rule]]](Right(Nil))((l, acc) => acc.flatMap(rs => parse(l).map(_ :: rs)))

  /** the default: a transfer above 500 blocked, one to a new payee asked, everything else passed */
  val default: List[Rule] = read(
    """block bank.transfer amount > 500 : over the limit
      |ask bank.transfer to == mallory : a new payee
      |pass *""".stripMargin).fold(sys.error, identity)
