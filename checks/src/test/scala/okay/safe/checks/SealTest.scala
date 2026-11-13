package okay.safe.checks

import java.io.File
import java.nio.file.{Files, Path, StandardCopyOption}
import java.util.jar.JarFile

import scala.jdk.CollectionConverters.*

import okay.safe.gate.{Deployment, Gate, Seal}

/*
 * THE DEPLOYMENT PROVES IT WAS CHECKED: the bank's application sealed by the gate, started by the kernel's Launch in
 * a JVM of its own — and refused when the seal is missing, a class changed or was added, the JVM lacks its flag,
 * the kernel is not the named module, a source lacks its import, or the build lacks a required flag.
 */
class SealTest extends munit.FunSuite:
  val classpath: List[Path] = Cases.classpath.split(File.pathSeparator).toList.map(Path.of(_))
  val kernel: Path = classpath.find(p => Files.isDirectory(p) && p.toString.contains("/core/")).get
  val library: Path = classpath.find(_.getFileName.toString.startsWith("scala-library")).get
  val sources: List[Path] = Gate.sources(Path.of(sys.props("okay.safe.example")))
  val host = List("okay.bank.host")
  val entry = "okay.bank.host.BankHost"

  def build(sources: List[Path] = sources, flags: List[String] = Gate.flags): Either[List[Gate.Reason], Deployment] =
    Seal.build(sources, host, entry, kernel, library, Files.createTempDirectory("okay-safe-deployment"), flags)

  lazy val sealedOnce: Deployment = build().fold(r => fail(s"refused:\n${r.mkString("\n")}"), identity)

  /** a copy of the deployment, to change */
  def copy(d: Deployment = sealedOnce): Deployment =
    val to = Files.createTempDirectory("okay-safe-deployment")
    Gate.files(d.dir).foreach { (name, f) =>
      Files.createDirectories(to.resolve(name).getParent)
      Files.copy(f, to.resolve(name), StandardCopyOption.REPLACE_EXISTING)
    }
    Deployment(to, to.resolve("app.jar"), to.resolve("app.args"))

  /** the jar rewritten: its entries changed by `f`, its manifest kept */
  def rewrite(jar: Path)(f: List[(String, Array[Byte])] => List[(String, Array[Byte])]): Unit =
    val j = JarFile(jar.toFile)
    val (attributes, entries) =
      try (j.getManifest.getMainAttributes.asScala.toMap.map((k, v) => k.toString -> v.toString),
        j.entries.asScala.filterNot(e => e.isDirectory || e.getName == "META-INF/MANIFEST.MF")
          .map(e => e.getName -> j.getInputStream(e).readAllBytes).toList)
      finally j.close()
    Seal.write(jar, f(entries), attributes - "Manifest-Version"): Unit

  /** `java <args>` in the deployment's directory: its exit and its output */
  def java(d: Deployment, args: String*): (Int, String) =
    val bin = Path.of(sys.props("java.home"), "bin", "java").toString
    val process = ProcessBuilder((bin +: args)*).directory(d.dir.toFile).redirectErrorStream(true).start()
    val out = String(process.getInputStream.readAllBytes)
    (process.waitFor(), out)

  def refused(d: Deployment, why: String, args: String*): Unit =
    val (exit, out) = java(d, (if args.isEmpty then Seq("@app.args") else args)*)
    assertEquals(exit, 2, out)
    assert(out.contains("okay-safe: refused to start") && out.contains(why), out)

  test("sealed: the gate's deployment starts, and the entry runs the audited payroll"):
    val (exit, out) = java(sealedOnce, "@app.args")
    assertEquals(exit, 0, out)
    assert(out.contains("Right(500)") && out.contains("asked: Transfer(company,ann,300)"), out)
    assert(out.contains("the service received: POST /transfers from=company&to=ann&amount=300"), out)
    val seal = String(JarFile(sealedOnce.jar.toFile).getInputStream(
      JarFile(sealedOnce.jar.toFile).getEntry(okay.audit.Seal.Entry)).readAllBytes)
    for line <- List("okay-audit-seal 1", "checker okay-audit", "config ", "result pass", s"compiler ${Gate.compiler}",
        "flags -experimental", "policy ", "whole okay-safe-whole", "whole-result clean",
        "entry okay.bank.host.BankHost", "jvm --sun-misc-unsafe-memory-access=deny", "module okay.safe ",
        "library scala.library ", "audit okay.audit ", "file ", "okay/bank/Payroll$.class") do
      assert(seal.contains(line), s"$line not in\n$seal")

  test("a class changed after the checks: refused"):
    val d = copy()
    rewrite(d.jar)(_.map((n, b) => if n == "okay/bank/Payroll$.class" then n -> (b :+ 0.toByte) else n -> b))
    refused(d, "okay/bank/Payroll$.class does not match its seal")

  test("a class added after the checks: refused"):
    val d = copy()
    rewrite(d.jar)(es => es :+ ("okay/bank/Extra.class" -> es.find(_._1 == "okay/bank/Payroll$.class").get._2))
    refused(d, "okay/bank/Extra.class is not in the seal")

  test("a class in the kernel's package: refused"):
    val d = copy()
    rewrite(d.jar)(es => es :+ ("okay/safe/Forged.class" -> Array[Byte](1)))
    refused(d, "okay/safe/Forged.class is in the kernel's package")

  test("no seal: refused"):
    val d = copy()
    rewrite(d.jar)(_.filterNot(_._1 == okay.audit.Seal.Entry))
    refused(d, "no seal in")

  test("the JVM without --sun-misc-unsafe-memory-access=deny: refused"):
    refused(copy(), "the JVM allows sun.misc.Unsafe's memory access",
      "--module-path", "mods", "--add-modules", "okay.safe", "-jar", "app.jar")

  test("the kernel on the class path, not the named module: refused"):
    val cp = List("app.jar", "mods/okay-safe.jar", "mods/scala-library.jar", "mods/okay-audit.jar")
      .mkString(File.pathSeparator)
    refused(copy(), "the class path must be the sealed jar alone",
      "--sun-misc-unsafe-memory-access=deny", "-cp", cp, "okay.safe.Launch")

  test("another kernel than the one sealed: refused"):
    val d = copy()
    rewrite(d.dir.resolve("mods/okay-safe.jar"))(_ :+ ("okay/safe/std/Extra.txt" -> Array[Byte](1)))
    refused(d, "the module okay.safe is not the one sealed")

  test("a program without `import language.experimental.safe`: the gate seals nothing"):
    val dir = Files.createTempDirectory("okay-safe-sources")
    val stripped = sources.map { f =>
      val to = dir.resolve(f.getFileName.toString)
      Files.writeString(to, Files.readString(f).replace("import language.experimental.safe\n", ""))
    }
    val reasons = build(stripped).fold(identity, d => fail(s"sealed: $d"))
    assert(reasons.exists(_.toString.contains("Bank.scala:1: not compiled in safe mode")), reasons.mkString("\n"))

  test("a build without a required flag: sealed by a gate set up wrong, refused at the start"):
    val d = build(flags = Gate.flags.filterNot(_ == "-Wsafe-init")).fold(r => fail(r.mkString("\n")), identity)
    refused(d, "compiled without -Wsafe-init")

  test("NOT SIGNED: a seal written by hand over a class the gate never saw is accepted — what a signature closes"):
    val d = copy()
    val forged = Array[Byte](1, 2, 3)
    rewrite(d.jar)(_.map {
      case (okay.audit.Seal.Entry, b) =>
        okay.audit.Seal.Entry -> (String(b) + s"file ${Seal.sha256(forged)} okay/bank/Forged.class\n").getBytes
      case other => other
    } :+ ("okay/bank/Forged.class" -> forged))
    assertEquals(java(d, "@app.args")._1, 0)

  test("okay-audit's bytecode witness: a class compiled from Java beside the program, which has no TASTy for the " +
      "whole-program check to read, is refused by the gate"):
    val classes = Files.createTempDirectory("okay-safe-java")
    val src = Files.createTempDirectory("okay-safe-java-src")
    Files.createDirectories(src.resolve("okay/bank"))
    val leak = Files.writeString(src.resolve("okay/bank/Leak.java"), "package okay.bank; public final class Leak { " +
      "public static int open() throws Exception { try (var s = new java.net.Socket(\"localhost\", 1)) { return 1; } } }")
    assertEquals(javax.tools.ToolProvider.getSystemJavaCompiler.run(null, null, null, "-d", classes.toString,
      leak.toString), 0)
    assertEquals(Gate.check(classes, classpath, okay.safe.whole.Policy.default.copy(host = host)), Nil)
    val (_, report) = Gate.audit(classes, host, List(kernel))
    assert(report.findings.exists(_.ref.member == "java.net.Socket#<init>"), report.text)
    assert(Gate.reasons(report).exists(_.toString.contains("okay-audit: application: okay.bank.Leak")),
      Gate.reasons(report).mkString("\n"))
