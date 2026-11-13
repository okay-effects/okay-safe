package okay.safe.gate

import java.io.File
import java.nio.file.{Files, Path, StandardCopyOption}
import java.security.MessageDigest
import java.util.HexFormat
import java.util.jar.{Attributes, JarOutputStream, Manifest}
import java.util.zip.ZipEntry
import javax.tools.ToolProvider

import okay.safe.whole.{Policy, Whole}

/** a sealed deployment: its directory, the application's jar, the JVM's arguments (`java @app.args`, run there) */
final case class Deployment(dir: Path, jar: Path, args: Path)

/*
 * THE SEAL: a build that proves it was checked. The gate compiles the application's sources itself — so the flags
 * recorded are the flags used, not the flags claimed —, holds them to the whole-program check, and only when both
 * pass writes a deployment:
 *
 *   mods/okay-safe.jar       the kernel as the named module `okay.safe` (module-info: exports, opens nothing)
 *   mods/scala-library.jar   the standard library, the automatic module `scala.library`
 *   mods/okay-audit.jar      okay-audit, the automatic module `okay.audit`: the seal's format and its verifier
 *   app.jar                  the application's classes and TASTy, and META-INF/okay-audit.seal; its manifest's
 *                            Main-Class is the kernel's Launch, and it records the JVM options and module path
 *   app.args                 those options, for `java @app.args`: the JVM flag, the module path, the jar
 *
 * The seal is okay-audit's (okay.audit.Seal): okay-audit's own check of the classes — the bytecode witness,
 * Gate.audit — with its configuration's hash and result, the JVM flag, every file of app.jar by its SHA-256; and
 * this gate's fields beside: the compiler's version, its flags, the policy's hash, the whole-program check's version
 * and its result, the entry, the kernel's, the library's and okay-audit's jars by their SHA-256. The kernel's Launch
 * verifies all of it with okay-audit's verifier before it gives the entry a runner (Launch.scala).
 *
 * NOT SIGNED. The seal proves the jar is the one the gate wrote and the settings it recorded are the required
 * ones; it does not prove who wrote it: anyone can recompute the hashes over classes compiled any way, and write a
 * seal that says "clean" (SealTest shows it). A signature by the gate's key, its public half pinned in the
 * launcher, would bind the seal to the gate — then a forged seal is refused too.
 */
object Seal:
  val jvm: List[String] = List("--sun-misc-unsafe-memory-access=deny")

  def sha256(bytes: Array[Byte]): String = HexFormat.of.formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
  def sha256(file: Path): String = sha256(Files.readAllBytes(file))

  /** the application of `sources` — programs in safe mode, its host in `host` — sealed into `out`; or why not.
   * `kernel` is the kernel's classes, `library` the standard library's jar, `audit` okay-audit's (the kernel's
   * Launch verifies the seal with it). */
  def build(sources: List[Path], host: List[String], entry: String, kernel: Path, library: Path, out: Path,
      flags: List[String] = Gate.flags, policy: Policy = Policy.default,
      audit: Path = Gate.auditJar): Either[List[Gate.Reason], Deployment] =
    val classes = Files.createTempDirectory("okay-safe-sealed")
    val classpath = List(kernel, library)
    val sealedPolicy = policy.copy(host = host)
    val errors = Gate.compile(sources, classpath, classes, flags)
    lazy val findings = Gate.check(classes, classpath, sealedPolicy)
    lazy val (boundary, report) = Gate.audit(classes, host, List(kernel))
    if errors.nonEmpty then Left(errors)
    else if findings.nonEmpty then Left(findings)
    else if !report.passed then Left(Gate.reasons(report))
    else
      Files.createDirectories(out.resolve("mods")): Unit
      val module = kernelModule(kernel, library, audit, out.resolve("mods/okay-safe.jar"))
      val lib = Files.copy(library, out.resolve("mods/scala-library.jar"), StandardCopyOption.REPLACE_EXISTING)
      val auditJar = Files.copy(audit, out.resolve("mods/okay-audit.jar"), StandardCopyOption.REPLACE_EXISTING)
      val files = Gate.files(classes)
      val units = files.count(_._1.endsWith(".tasty"))
      // okay-audit's seal: its result and configuration, the JVM's flag, every file; this gate's fields beside
      val seal = okay.audit.Seal.of(report, boundary, files.toVector.map((n, f) => n -> sha256(f)), Vector(
        "compiler" -> Gate.compiler,
        "flags" -> flags.mkString(" "),
        "policy" -> sealedPolicy.hash,
        "whole" -> Whole.version,
        "whole-result" -> s"clean: $units units, no error, no finding",
        "host" -> host.mkString(" "),
        "entry" -> entry,
        "module" -> s"okay.safe ${sha256(module)}",
        "library" -> s"scala.library ${sha256(lib)}",
        "audit" -> s"okay.audit ${sha256(auditJar)}"), jvm.toVector)
      val jar = out.resolve("app.jar")
      val options = jvm ++ List("--module-path", "mods", "--add-modules", "okay.safe")
      Seal.write(jar, files.map((n, f) => n -> Files.readAllBytes(f)) :+
          (okay.audit.Seal.Entry -> seal.text.getBytes("UTF-8")),
        Map("Main-Class" -> "okay.safe.Launch", "Okay-Safe-JVM-Options" -> jvm.mkString(" "),
          "Okay-Safe-Module-Path" -> "mods/okay-safe.jar mods/scala-library.jar mods/okay-audit.jar",
          "Okay-Safe-Add-Modules" -> "okay.safe")): Unit
      val args = Files.writeString(out.resolve("app.args"), (options ++ List("-jar", "app.jar")).mkString("\n") + "\n")
      Right(Deployment(out, jar, args))

  /** a jar of `entries`, in their order, with a manifest of `attributes` */
  def write(jar: Path, entries: List[(String, Array[Byte])], attributes: Map[String, String]): Path =
    val manifest = Manifest()
    manifest.getMainAttributes.put(Attributes.Name.MANIFEST_VERSION, "1.0")
    attributes.toList.sorted.foreach((k, v) => manifest.getMainAttributes.put(Attributes.Name(k), v))
    val stream = JarOutputStream(Files.newOutputStream(jar), manifest)
    try entries.foreach { (name, bytes) =>
      stream.putNextEntry(ZipEntry(name))
      stream.write(bytes)
      stream.closeEntry()
    }
    finally stream.close()
    jar

  /** the kernel's classes as the named module `okay.safe`: `okay.safe` and `okay.safe.std` exported, nothing
   * opened; it reads okay-audit, whose verifier its Launch calls */
  def kernelModule(kernel: Path, library: Path, audit: Path, to: Path): Path =
    val dir = Files.createTempDirectory("okay-safe-module")
    val src = Files.createTempDirectory("okay-safe-module-src")
    val files = Gate.files(kernel)
    files.foreach { (name, f) =>
      val at = dir.resolve(name)
      Files.createDirectories(at.getParent)
      Files.copy(f, at)
    }
    val info = Files.writeString(src.resolve("module-info.java"),
      "module okay.safe { requires scala.library; requires okay.audit; exports okay.safe; exports okay.safe.std; }")
    val javac = ToolProvider.getSystemJavaCompiler.run(null, null, null, "--module-path",
      List(library, audit).mkString(File.pathSeparator),
      "--patch-module", s"okay.safe=$dir", "-d", dir.toString, info.toString)
    require(javac == 0, "javac could not compile the kernel's module-info")
    write(to, Gate.files(dir).map((n, f) => n -> Files.readAllBytes(f)), Map.empty)

/** `okay.safe.gate.Seal --sources <dir> --host <pkg,...> --entry <class> --kernel <classes> --library <jar>
 * --out <dir>`: the deployment, or the reasons it was refused (exit 1) */
object Main:
  def main(args: Array[String]): Unit =
    def opt(name: String): String =
      args.sliding(2).collectFirst { case Array(`name`, v) => v }.getOrElse(sys.error(s"$name is required"))
    val sources = opt("--sources").split(File.pathSeparator).toList.flatMap(d => Gate.sources(Path.of(d)))
    Seal.build(sources, opt("--host").split(',').toList, opt("--entry"), Path.of(opt("--kernel")),
      Path.of(opt("--library")), Path.of(opt("--out"))) match
      case Right(d) => println(s"sealed: ${d.jar}\nrun: cd ${d.dir} && java @app.args")
      case Left(reasons) =>
        reasons.foreach(println)
        sys.exit(1)
