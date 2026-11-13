package okay.safe.whole

import java.io.File
import java.nio.file.Path

/** `okay.safe.whole.Main --program <paths> --classpath <paths> [--host <pkg>,...] [--explicit-nulls]`: the findings,
 * one a line; exit 1 when there is one. Paths are separated as on a class path. */
object Main:
  def main(args: Array[String]): Unit =
    def opt(name: String): List[String] =
      args.sliding(2).collect { case Array(`name`, v) => v }.toList
    def paths(name: String): List[Path] =
      opt(name).flatMap(_.split(File.pathSeparator).toList).filter(_.nonEmpty).map(Path.of(_))
    val policy = Policy.default.copy(host = opt("--host").flatMap(_.split(',').toList),
      explicitNulls = args.contains("--explicit-nulls"))
    val findings = Whole.check(paths("--program"), paths("--classpath"), policy)
    findings.foreach(println)
    if findings.nonEmpty then sys.exit(1) else println("no findings")
