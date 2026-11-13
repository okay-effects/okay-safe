package okay.safe

import java.nio.file.{Files, Path}
import java.security.MessageDigest
import java.util.HexFormat
import java.util.jar.JarFile

import scala.jdk.CollectionConverters.*

/*
 * THE ENTRY OF AN APPLICATION, AND THE PROOF THAT IT WAS CHECKED. Part of the trusted base: compiled with no checks,
 * `private[safe]`, so no code outside the kernel can name it — the JVM starts it (`Main-Class: okay.safe.Launch`).
 * It is the one caller of `Runner.host`: it gives a runner ONCE, to the application's `Entry`, after it has verified
 * the application's SEAL (written by the gate, module `gate`):
 *
 *   - the class path is the sealed jar alone, and the jar holds the seal;
 *   - every file of the jar is in the seal with its SHA-256, and every file of the seal is in the jar;
 *   - the seal records what the checks require: the compiler's flags, the whole-program check passed, its policy;
 *   - the JVM refuses `sun.misc.Unsafe`'s memory access (`--sun-misc-unsafe-memory-access=deny`): tried, not read;
 *   - the kernel runs as the named module `okay.safe`, nothing opened, from the jar the seal names by its hash, and
 *     the standard library from its own.
 *
 * Any of these missing: it refuses to start. `-Dokay.safe.unsealed=<why>` starts `-Dokay.safe.entry=<class>` with
 * nothing verified, and says so: development, tests, the benchmark — the operator's switch, as every JVM flag is.
 */
private[safe] object Launch:
  private val launched = java.util.concurrent.atomic.AtomicBoolean()

  def main(args: Array[String]): Unit =
    if !launched.compareAndSet(false, true) then refuse("an application is entered once: Launch ran already")
    val name = sys.props.get("okay.safe.unsealed") match
      case Some(why) =>
        System.err.println(s"okay-safe: UNSEALED ($why): the checks of this application are not verified")
        sys.props.getOrElse("okay.safe.entry", refuse("no entry: -Dokay.safe.entry=<class>"))
      case None => Sealed.verify().fold(refuse, identity)
    val entry = load(name).fold(refuse, identity)
    Runner.host(entry.run(args.toList))

  private def refuse(why: String): Nothing =
    System.err.println(s"okay-safe: refused to start: $why")
    sys.exit(2)

  /** the entry: an object, or a class made with no arguments, that is an `Entry` */
  def load(name: String): Either[String, Entry] =
    val loader = ClassLoader.getSystemClassLoader
    def instance: Any =
      try Class.forName(name + "$", true, loader).getField("MODULE$").get(null)
      catch case _: ClassNotFoundException => Class.forName(name, true, loader).getConstructor().newInstance()
    try instance match
      case e: Entry => Right(e)
      case _ => Left(s"the entry $name is not an okay.safe.Entry")
    catch case e: ReflectiveOperationException => Left(s"no entry $name: $e")

/** THE SEAL: what the gate recorded of a build, and its verification at the entry */
private[safe] object Sealed:
  /** where the seal is, in the application's jar */
  val file = "META-INF/okay-safe.seal"
  val format = "1"
  /** what a build must have been compiled with: every warning an error, initialization checked */
  val requiredFlags = List("-Werror", "-Wsafe-init")
  val deny = "--sun-misc-unsafe-memory-access=deny"

  def sha256(bytes: Array[Byte]): String = HexFormat.of.formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))

  /** a seal: its fields (`key value`), and its files (`file <sha-256> <path>`) */
  final case class Seal(fields: Map[String, String], files: Map[String, String]):
    def apply(key: String): Either[String, String] = fields.get(key).toRight(s"the seal records no $key")

  def parse(text: String): Seal =
    val lines = text.linesIterator.map(_.trim).filter(_.nonEmpty).map(l => l.span(_ != ' ')).toList
    Seal(lines.collect { case (k, v) if k != "file" => k -> v.trim }.toMap,
      lines.collect { case ("file", v) => v.trim.span(_ != ' ') }.map((sha, path) => path.trim -> sha).toMap)

  /** the entry's name, or why the application may not start */
  def verify(): Either[String, String] =
    for
      jar <- applicationJar
      seal <- read(jar)
      _ <- files(jar, seal)
      _ <- settings(seal)
      _ <- unsafeDenied
      _ <- module(seal)
      entry <- seal("entry")
    yield entry

  /** the class path: the sealed jar, and nothing else */
  def applicationJar: Either[String, Path] =
    sys.props("java.class.path").split(java.io.File.pathSeparator).filter(_.nonEmpty).toList match
      case List(one) if one.endsWith(".jar") && Files.isRegularFile(Path.of(one)) => Right(Path.of(one))
      case other => Left(s"the class path must be the sealed jar alone: ${other.mkString(java.io.File.pathSeparator)}")

  def read(jar: Path): Either[String, Seal] =
    val j = JarFile(jar.toFile)
    try Option(j.getEntry(file)).map(e => parse(String(j.getInputStream(e).readAllBytes, "UTF-8")))
      .toRight(s"no seal in $jar: it was not built by the gate")
    finally j.close()

  /** every file of the jar sealed, as it was; none missing */
  def files(jar: Path, seal: Seal): Either[String, Unit] =
    val j = JarFile(jar.toFile)
    try
      val present = j.entries.asScala.filterNot(_.isDirectory).map(_.getName)
        .filterNot(n => n == file || n == "META-INF/MANIFEST.MF")
        .map(n => n -> sha256(j.getInputStream(j.getEntry(n)).readAllBytes)).toMap
      present.keys.find(!seal.files.contains(_)).map(n => s"$n is not in the seal: added after the checks")
        .orElse(present.collectFirst { case (n, sha) if seal.files(n) != sha =>
          s"$n does not match its seal: changed after the checks" })
        .orElse(seal.files.keys.find(!present.contains(_)).map(n => s"$n of the seal is not in the jar"))
        .toLeft(())
    finally j.close()

  /** the settings the guarantee is stated under, as the seal records them */
  def settings(seal: Seal): Either[String, Unit] =
    for
      f <- seal("okay-safe-seal")
      _ <- Either.cond(f == format, (), s"a seal of format $f, not $format")
      _ <- seal("compiler")
      flags <- seal("flags").map(_.split(' ').toSet)
      _ <- requiredFlags.find(!flags(_)).map(m => s"compiled without $m").toLeft(())
      _ <- seal("policy")
      _ <- seal("whole")
      result <- seal("result")
      _ <- Either.cond(result.startsWith("clean"), (), s"the whole-program check did not pass: $result")
      jvm <- seal("jvm")
      _ <- Either.cond(jvm.split(' ').contains(deny), (), s"the seal does not ask $deny")
    yield ()

  /** `sun.misc.Unsafe`'s memory access refused by the JVM: tried on a field of the kernel's, nothing written */
  def unsafeDenied: Either[String, Unit] =
    try
      scala.runtime.LazyVals.getOffset(classOf[Cell[?]], "state"): Unit
      Left(s"the JVM allows sun.misc.Unsafe's memory access: start it with $deny")
    catch case _: UnsupportedOperationException => Right(())

  /** the kernel the named module `okay.safe`, closed, from the jar the seal names; the library from its own */
  def module(seal: Seal): Either[String, Unit] =
    val kernel = classOf[Runner].getModule
    def location(m: Module): Option[Path] =
      Option(m.getLayer).flatMap(_.configuration.findModule(m.getName).toScala)
        .flatMap(_.reference.location.toScala).map(Path.of(_))
    def hashed(m: Module, key: String): Either[String, Unit] =
      for
        recorded <- seal(key)
        at <- location(m).toRight(s"the module ${m.getName} has no jar")
        sha = sha256(Files.readAllBytes(at))
        _ <- Either.cond(recorded == s"${m.getName} $sha", (), s"the module ${m.getName} is not the one sealed: $at")
      yield ()
    for
      _ <- Either.cond(kernel.isNamed && kernel.getName == "okay.safe", (),
        "the kernel is not the named module okay.safe: put it on the module path, --add-modules okay.safe")
      _ <- Either.cond(!kernel.getDescriptor.isOpen && kernel.getDescriptor.opens.isEmpty, (),
        "the kernel's module opens its packages to reflection")
      _ <- hashed(kernel, "module")
      _ <- hashed(classOf[Option[?]].getModule, "library")
    yield ()

  extension [A](o: java.util.Optional[A]) private def toScala: Option[A] = if o.isPresent then Some(o.get) else None
