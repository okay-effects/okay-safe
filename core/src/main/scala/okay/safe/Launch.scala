package okay.safe

import java.nio.file.{Files, Path}

import okay.audit.Seal

/*
 * THE ENTRY OF AN APPLICATION, AND THE PROOF THAT IT WAS CHECKED. Part of the trusted base: compiled with no checks,
 * `private[safe]`, so no code outside the kernel can name it — the JVM starts it (`Main-Class: okay.safe.Launch`).
 * It is the one caller of `Runner.host`: it gives a runner ONCE, to the application's `Entry`, after it has verified
 * the application's SEAL (written by the gate, module `gate`):
 *
 *   - the class path is the sealed jar alone, and the jar holds the seal (okay-audit's, verified by okay-audit);
 *   - every file of the jar is in the seal with its SHA-256, and every file of the seal is in the jar; none is in
 *     the kernel's packages (`private[safe]` reaches every package under `okay.safe`);
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

/** THE SEAL: what the gate recorded of a build, and its verification at the entry. The seal's format, its files'
 * check, its result and the JVM's options are okay-audit's (okay.audit.Seal, the one verifier of the family); what is
 * the kernel's own is asked here: the flags the guarantee is stated under, the whole-program check passed, the JVM
 * refusing `sun.misc.Unsafe` (tried), the kernel and the libraries the named modules sealed */
private[safe] object Sealed:
  /** what a build must have been compiled with: every warning an error, initialization checked */
  val requiredFlags = List("-Werror", "-Wsafe-init")
  val deny = "--sun-misc-unsafe-memory-access=deny"

  def sha256(bytes: Array[Byte]): String = Seal.sha256(bytes)

  /** the entry's name, or why the application may not start */
  def verify(): Either[String, String] =
    for
      jar <- applicationJar
      seal <- Seal.read(jar)
      _ <- Seal.verifyResult(seal)
      _ <- Seal.verifyFiles(seal, Seal.files(jar, n => n == Seal.Entry || n == "META-INF/MANIFEST.MF"),
        n => Option.when(n.startsWith("okay/safe/"))(s"$n is in the kernel's package: refused"))
      _ <- settings(seal)
      // the seal's JVM option is TRIED, not read from the JVM's arguments (okay-audit's Seal.verifyJvm reads them,
      // through java.management, at a start's cost): a JVM that refuses Unsafe is the fact the option stands for
      _ <- unsafeDenied
      _ <- module(seal)
      entry <- seal("entry")
    yield entry

  /** the class path: the sealed jar, and nothing else */
  def applicationJar: Either[String, Path] =
    sys.props("java.class.path").split(java.io.File.pathSeparator).filter(_.nonEmpty).toList match
      case List(one) if one.endsWith(".jar") && Files.isRegularFile(Path.of(one)) => Right(Path.of(one))
      case other => Left(s"the class path must be the sealed jar alone: ${other.mkString(java.io.File.pathSeparator)}")

  /** the settings the guarantee is stated under, as the seal records them */
  def settings(seal: Seal): Either[String, Unit] =
    for
      _ <- seal("compiler")
      flags <- seal("flags").map(_.split(' ').toSet)
      _ <- requiredFlags.find(!flags(_)).map(m => s"compiled without $m").toLeft(())
      _ <- seal("policy")
      _ <- seal("whole")
      result <- seal("whole-result")
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

  /** the kernel the named module `okay.safe`, closed, from the jar the seal names; the library and okay-audit from
   * their own */
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
      _ <- hashed(classOf[Seal].getModule, "audit")
    yield ()

  extension [A](o: java.util.Optional[A]) private def toScala: Option[A] = if o.isPresent then Some(o.get) else None
