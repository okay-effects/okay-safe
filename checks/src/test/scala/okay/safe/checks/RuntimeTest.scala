package okay.safe.checks

import java.lang.module.{Configuration, ModuleFinder, ResolutionException}
import java.nio.file.{Files, Path}
import java.util.concurrent.atomic.AtomicInteger
import javax.tools.ToolProvider

import scala.jdk.CollectionConverters.*

import okay.safe.*
import okay.safe.std.*
import okay.safe.example.host.{BankClient, BankHost}

/** an operation whose answer the host gives later, by resuming the `k` it was handed */
enum Ask[X]:
  case Number() extends Ask[Int]

/*
 * THE CHECKS OF LAST RESORT, AND WHAT THE TYPES LEAVE TO RUN TIME. This file is not capture checked — as a host not
 * written with capture checking — so it can do what the cases show refused, and see what happens then.
 */
class RuntimeTest extends munit.FunSuite:

  test("1. a run inside a running program is refused at run time: NestedRun"):
    // a host not capture checked runs a program with its runner inside a continuation: the types let it through
    hosted:
      val inner: Int => Int ! Pure = x => pure(run(pure[Int, Pure](x + 1)))
      intercept[NestedRun](run(pure[Int, Pure](1).flatMap(inner))): Unit
    // and the thread is left able to run: the flag is reset
    assertEquals(hosted(run(pure[Int, Pure](1).flatMap(x => pure(x + 1)))), 2)

  test("2. k kept after its handler returned is a value: resumed later, many times, nothing replayed"):
    val prefix = AtomicInteger()
    type Out = Either[Int, Continuation[Int, Pure, Any]]
    val body: Int ! Ask =
      Comp.Bind(pure[Unit, Ask](()), _ => { prefix.incrementAndGet(): Unit; word(Ask.Number()).into[Ask] })
    val p: Int ! Ask = body.flatMap(n => State.run(n)(State.get[Int].flatMap(s => State.put(s * 10).map(_ => s)))
      .into[Ask].map((s, a) => s + a))
    val h = Handler.control[Ask, Pure, Int, Out](a => pure(Left(a)))(
      [X] => (op: Ask[X], k: Continuation[X, Pure, Out]) => op match
        case Ask.Number() => pure(Right(k)))
    val k = hosted(run(handle(p, h))) match
      case Right(k) => k
      case Left(a) => fail(s"no question: $a")
    val answers = hosted(List(5, 7, 5).map(n => run(k(n))))
    assertEquals(answers, List(Left(55), Left(77), Left(55)))
    assertEquals(prefix.get, 1)

  test("3/10. LazyVals rewrites an immutable list from code safe mode accepts (the case immutable-by-lazyvals)"):
    val xs = List(1, 2, 3)
    val offset = scala.runtime.LazyVals.getOffset(classOf[::[?]], "next")
    assert(scala.runtime.LazyVals.objCAS(xs, offset, xs.tail, List(9)))
    assertEquals(xs, List(1, 9))

  test("4. the bank: only its handler reaches the client, and only what the audit passed"):
    val client = BankClient(Map("company" -> 1000L))
    val (trail, result) = hosted(BankHost.payroll(client, 500, List("ann" -> 300L, "bob" -> 600L)))
    assertEquals(result, Left("over the limit: 600"))
    assertEquals(trail.last, "refused: 600 from company to bob")
    // balance, transfer to ann, balance: the refused transfer never reached the client
    assertEquals(BankHost.calls(client), 3)
    assertEquals(BankHost.holds(client, "bob"), 0L)

  /** the kernel as an explicit module: `okay.safe` and `okay.safe.std` exported, nothing opened */
  def kernelModule(): Path =
    val dir = Files.createTempDirectory("okay-safe-module")
    val src = Files.createTempDirectory("okay-safe-module-src")
    val classes = Cases.classpath.split(java.io.File.pathSeparator).map(Path.of(_))
    val core = classes.find(p => Files.isDirectory(p) && p.toString.contains("/core/")).get
    val library = classes.find(_.getFileName.toString.startsWith("scala-library")).get
    Files.walk(core).iterator.asScala.filter(Files.isRegularFile(_)).foreach { f =>
      val to = dir.resolve(core.relativize(f).toString)
      Files.createDirectories(to.getParent)
      Files.copy(f, to)
    }
    Files.writeString(src.resolve("module-info.java"),
      "module okay.safe { requires scala.library; exports okay.safe; exports okay.safe.std; }")
    javac("--module-path", library.toString, "--patch-module", s"okay.safe=$dir", "-d", dir.toString,
      src.resolve("module-info.java").toString)
    dir

  def javac(args: String*): Unit =
    assertEquals(ToolProvider.getSystemJavaCompiler.run(null, null, null, args*), 0, args.mkString(" "))

  def library: Path =
    Cases.classpath.split(java.io.File.pathSeparator).map(Path.of(_))
      .find(_.getFileName.toString.startsWith("scala-library")).get

  test("JPMS: a program cannot add a class to the kernel's package — a split package is refused"):
    val kernel = kernelModule()
    val app = Files.createTempDirectory("okay-safe-app")
    val src = Files.createTempDirectory("okay-safe-app-src")
    Files.createDirectories(src.resolve("okay/safe"))
    Files.writeString(src.resolve("okay/safe/Forged.java"), "package okay.safe; public class Forged {}")
    javac("-d", app.toString, src.resolve("okay/safe/Forged.java").toString)
    Files.writeString(src.resolve("module-info.java"), "module app { requires okay.safe; }")
    javac("--module-path", s"$kernel${java.io.File.pathSeparator}$library", "-d", app.toString,
      src.resolve("module-info.java").toString)
    val finder = ModuleFinder.of(kernel, library, app)
    val refused = intercept[ResolutionException](
      Configuration.resolve(finder, List(ModuleLayer.boot.configuration).asJava, ModuleFinder.of(),
        List("app").asJava))
    assert(refused.getMessage.contains("okay.safe"), refused.getMessage)

  test("JPMS: the kernel's state is closed to reflection"):
    val kernel = kernelModule()
    val finder = ModuleFinder.of(kernel, library)
    val config = ModuleLayer.boot.configuration.resolve(finder, ModuleFinder.of(), List("okay.safe").asJava)
    val layer = ModuleLayer.boot.defineModulesWithOneLoader(config, ClassLoader.getPlatformClassLoader)
    val cell = layer.findLoader("okay.safe").loadClass("okay.safe.Cell")
    assertEquals(cell.getModule.getName, "okay.safe")
    intercept[java.lang.reflect.InaccessibleObjectException](cell.getDeclaredField("state").setAccessible(true)): Unit

  test("run time: the JVM refuses sun.misc.Unsafe when told to — LazyVals cannot rewrite then"):
    def probe(flags: String*): String =
      val java = Path.of(sys.props("java.home"), "bin", "java").toString
      val command = (java +: flags) ++ Seq("-cp", sys.props("java.class.path"), "okay.safe.checks.LazyValsProbe")
      val process = ProcessBuilder(command*).redirectErrorStream(true).start()
      val out = String(process.getInputStream.readAllBytes)
      process.waitFor(): Unit
      out
    assert(probe().contains("rewritten: List(1, 9)"))
    assert(probe("--sun-misc-unsafe-memory-access=deny").contains("refused: java.lang.UnsupportedOperationException"))

/** what `immutable-by-lazyvals` does, as a program of its own */
object LazyValsProbe:
  def main(args: Array[String]): Unit =
    val xs = List(1, 2, 3)
    try
      val offset = scala.runtime.LazyVals.getOffset(classOf[::[?]], "next")
      scala.runtime.LazyVals.objCAS(xs, offset, xs.tail, List(9)): Unit
      println(s"rewritten: $xs")
    catch case e: Exception => println(s"refused: ${e.getClass.getName}: ${e.getMessage}")
