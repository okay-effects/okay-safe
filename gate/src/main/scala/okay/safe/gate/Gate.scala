package okay.safe.gate

import java.io.File
import java.nio.file.{Files, Path}

import scala.jdk.CollectionConverters.*

import dotty.tools.dotc.Driver
import dotty.tools.dotc.core.Contexts.Context
import dotty.tools.dotc.interfaces.Diagnostic.{ERROR, WARNING}
import dotty.tools.dotc.reporting.{Diagnostic, Reporter}

import okay.audit.{Audit, Boundary, Layer, Presets, Report, Types}
import okay.safe.whole.{Finding, Policy, Whole}

/*
 * THE GATE: nothing passes it unchecked. It compiles sources with the flags the guarantee is stated under — every
 * phase, capture checking and safe mode included, as the files ask them —, then holds what was compiled to the
 * whole-program check. What it passes is sealed (Seal.scala) or run (the agent's runner); what it does not is
 * refused with the reasons: the compiler's errors, the check's findings.
 */
object Gate:
  /** every lint, every warning an error, initialization checked: what the kernel and the application are compiled
   * with (build.sbt), and what a seal must record (the kernel's Launch asks `-Werror` and `-Wsafe-init` of it) */
  val flags: List[String] = List("-experimental", "-deprecation", "-feature", "-unchecked", "-Werror",
    "-Wunused:all", "-Wvalue-discard", "-Wnonunit-statement", "-Wimplausible-patterns", "-Wsafe-init")

  /** the compiler that compiles here */
  def compiler: String = dotty.tools.dotc.config.Properties.versionNumberString

  /** a reason a gate refuses: where, and what */
  final case class Reason(file: String, line: Int, what: String):
    override def toString: String = if file.isEmpty then what else s"$file:$line: $what"

  private final class Collect extends Reporter:
    val all = scala.collection.mutable.ListBuffer[Reason]()
    def doReport(d: Diagnostic)(using Context): Unit =
      if (d.level == ERROR || d.level == WARNING) && !werror(d.message) then
        val (file, line) = if d.pos.exists then (d.pos.source.file.name, d.pos.line + 1) else ("", 0)
        all += Reason(file, line, d.message)

  /** -Werror's own line, and the counts at the end of a run: no diagnostic of their own */
  private def werror(text: String): Boolean = text == "No warnings can be incurred under -Werror" ||
    text.linesIterator.forall(_.trim.matches("""\d+ (errors?|warnings?) found"""))

  /** `sources` compiled into `out` against `classpath`: none when it compiled; else its errors, and its warnings
   * (under -Werror a warning fails the run, and the error is only "No warnings can be incurred") */
  def compile(sources: List[Path], classpath: List[Path], out: Path, flags: List[String] = flags): List[Reason] =
    Files.createDirectories(out): Unit
    val args = flags ++ List("-color:never", "-d", out.toString, "-classpath",
      classpath.mkString(File.pathSeparator)) ++ sources.map(_.toString)
    val reporter = Collect()
    Driver().process(args.toArray, reporter): Unit
    if !reporter.hasErrors then Nil
    else if reporter.all.isEmpty then List(Reason("", 0, "the compiler failed, and said nothing more"))
    else reporter.all.toList

  /** what was compiled into `out`, held to the whole-program check: the findings, none when it passed */
  def check(out: Path, classpath: List[Path], policy: Policy): List[Reason] =
    Whole.check(List(out), classpath, policy).map((f: Finding) => Reason(f.file, f.line, f.what))

  /** THE BYTECODE WITNESS (okay-audit): what was compiled into `out`, held to okay-audit's rules — its default and its
   * presets named after the standard effects — with the program's packages as business, the host's as handlers, the
   * kernel as the runtime (the standard library is read for the hierarchy, its classes judged by the data list);
   * and to its boundary rule on types, okay-audit's data list with the kernel's. It reads what TASTy does not carry —
   * a class compiled from Java beside the program, what any JVM language's host gives it — and its result is the
   * seal's. */
  def audit(out: Path, host: List[String], runtime: List[Path]): (Boundary, Report) =
    val boundary = Boundary(Map("application" -> Layer.Business, "runtime" -> Layer.Runtime),
      rules = Presets.rules("default, pure"), packages = Map("application" -> host.map(_ -> Layer.Handlers).toMap),
      types = Some(Types(Types.Data ++ Policy.kernelData)))
    (boundary, Audit.run(boundary, Map("application" -> List(out), "runtime" -> runtime)))

  /** okay-audit's findings as a gate's reasons */
  def reasons(report: Report): List[Reason] =
    report.findings.toList.map(f => Reason("", 0,
      s"okay-audit: ${f.module}: ${f.ref.from} -> ${f.ref.member}${f.place}: ${f.rule.why}${f.rule.hint}"))

  /** okay-audit's jar, as this gate was given it */
  def auditJar: Path = Path.of(classOf[Report].getProtectionDomain.getCodeSource.getLocation.toURI)

  /** the sources under `dir`, every `.scala` file */
  def sources(dir: Path): List[Path] =
    Files.walk(dir).iterator.asScala.filter(_.toString.endsWith(".scala")).toList.sortBy(_.toString)

  /** every file under `dir`, by its path there with `/` */
  def files(dir: Path): List[(String, Path)] =
    Files.walk(dir).iterator.asScala.filter(Files.isRegularFile(_)).toList
      .map(f => dir.relativize(f).toString.replace(File.separatorChar, '/') -> f).sortBy(_._1)
