package okay.safe.checks

import java.io.File
import java.nio.file.{Files, Path}

import scala.jdk.CollectionConverters.*

import dotty.tools.dotc.Driver
import dotty.tools.dotc.core.Contexts.Context
import dotty.tools.dotc.interfaces.Diagnostic.{ERROR, WARNING}
import dotty.tools.dotc.reporting.{Diagnostic, Reporter}

import okay.safe.whole.{Finding, Policy, Whole}

/*
 * THE CASES: source files compiled by the compiler itself, as an application would compile them — every phase,
 * capture checking and safe mode included (a `compileErrors` stops after typing). A case says what it expects on the
 * line it expects it:
 *
 *   // error: <part of the message>    the compiler reports an error here
 *   // whole: <part of the message>    it compiles, and the whole-program check reports this here
 *
 * and in its first lines `//> flags: <options>` for the compiler, `//> host: <packages>` for the check. A case with
 * an `error` must report those errors, and none on a line that expects none; a case with none must compile with no error, and the
 * whole-program check must report its `whole` lines, and nothing on a line that expects nothing.
 */
object Cases:
  val classpath: String = sys.props("okay.safe.classpath")
  val root: Path = Path.of(sys.props("okay.safe.cases"))

  final case class Expected(file: String, line: Int, kind: String, text: String)
  final case class Reported(file: String, line: Int, level: Int, text: String)

  def expectations(file: Path): List[Expected] =
    val Mark = """.*//\s*(error|whole):\s*(.+)$""".r
    Files.readAllLines(file).asScala.toList.zipWithIndex.collect { case (Mark(kind, text), i) =>
      Expected(file.getFileName.toString, i + 1, kind, text.trim)
    }

  def header(file: Path, name: String): List[String] =
    val Head = s"""//>\\s*$name:\\s*(.*)$$""".r
    Files.readAllLines(file).asScala.toList.collect { case Head(v) => v.trim.split("\\s+").toList }.flatten

  private final class Collect extends Reporter:
    val all = scala.collection.mutable.ListBuffer[Reported]()
    def doReport(d: Diagnostic)(using Context): Unit =
      val (file, line) = if d.pos.exists then (d.pos.source.file.name, d.pos.line + 1) else ("", 0)
      all += Reported(file, line, d.level, d.message)

  /** A CASE: one file, or a directory of files, each its own compilation run in the order of their names — so a
   * dependency compiled one way is on the class path of a program compiled another */
  final case class Case(name: String, files: List[Path]):
    def expected: List[Expected] = files.flatMap(expectations)
    def headers(name: String): List[String] = files.flatMap(header(_, name))

    /** each file compiled into `out`, with the kernel, the example and what was compiled before on the class path */
    def compile(out: Path): List[Reported] = files.flatMap { file =>
      val args = List("-experimental", "-color:never", "-d", out.toString, "-classpath",
        classpath + File.pathSeparator + out) ++ header(file, "flags") :+ file.toString
      val reporter = Collect()
      Driver().process(args.toArray, reporter): Unit
      reporter.all.toList.filter(r => (r.level == ERROR || r.level == WARNING) && !r.text.matches("\\w+ (errors?|warnings?) found") &&
        r.text != "No warnings can be incurred under -Werror")
    }

    def whole(out: Path): List[Finding] =
      Whole.check(List(out), classpath.split(File.pathSeparator).toList.map(Path.of(_)),
        Policy.default.copy(host = headers("host"), explicitNulls = headers("require").contains("explicit-nulls")))

  /** the cases of a group: its files, and its directories */
  def cases(group: String): List[Case] =
    Files.list(root.resolve(group)).iterator.asScala.toList.sortBy(_.toString).collect {
      case f if f.toString.endsWith(".scala") => Case(f.getFileName.toString, List(f))
      case d if Files.isDirectory(d) =>
        Case(d.getFileName.toString + "/",
          Files.list(d).iterator.asScala.filter(_.toString.endsWith(".scala")).toList.sortBy(_.toString))
    }

  def groups: List[String] =
    Files.list(root).iterator.asScala.filter(Files.isDirectory(_)).map(_.getFileName.toString).toList.sorted
