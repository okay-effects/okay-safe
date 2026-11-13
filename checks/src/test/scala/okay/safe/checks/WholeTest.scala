package okay.safe.checks

import java.io.File
import java.nio.file.{Files, Path}

import okay.safe.whole.{Policy, Whole}

/** the whole-program check of the application in `example`: clean, its host apart; and what it finds in the host
 * when the host is held to the rules of programs */
class WholeTest extends munit.FunSuite:
  val classpath: List[Path] = Cases.classpath.split(File.pathSeparator).toList.map(Path.of(_))
  val example: Path = classpath.find(p => Files.isDirectory(p) && p.toString.contains("/example/")).get
  val trusted: List[Path] = classpath.filterNot(_ == example)

  test("the bank's application: no finding, its host at the edge"):
    assertEquals(Whole.check(List(example), trusted, Policy.default.copy(host = List("okay.safe.example.host"))), Nil)

  test("the host held to the rules of programs: everything a host does is found"):
    val found = Whole.check(List(example), trusted, Policy.default).map(_.toString)
    for what <- List("BankHost.scala:1: not compiled in safe mode", "okay.safe.Authority.tail", "okay.safe.run:",
        "scala.Predef.println", "java.util.concurrent.ConcurrentHashMap") do
      assert(found.exists(_.contains(what)), s"$what not in\n${found.mkString("\n")}")
    assert(!found.exists(_.startsWith("Bank.scala")), found.mkString("\n"))
