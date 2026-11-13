package okay.safe.whole

/** WHAT A PROGRAM MAY NAME outside itself. Default deny: a symbol of a library — the standard library, the JDK, the
 * kernel — is allowed when an `allow` entry is a prefix of its path and no `deny` entry is. Paths are the owners'
 * names joined by dots, objects without `$`, package objects left out: `scala.util.Random.nextInt`,
 * `okay.safe.run`. An entry `*.name` matches any path whose last part is `name`; `p.*.name` those of them under `p`.
 *
 * `kernel`: the packages of the trusted base, which no unit — a program's, the host's — may declare classes into,
 * nor into a package under them (package-private access: `private[safe]` reaches `okay.safe.anything`).
 * `host`: the packages of the host's edge, inspected but not held to the rules of programs, and which a
 * program may not name. `explicitNulls`: every program unit compiled with `-Yexplicit-nulls`.
 * `data`: the types, beside the built-in ones (Boundary in Whole.scala), that may cross the boundary — a handler's
 * answer, what the host gives a program —: final values of a library, and the kernel's evidence of rows. */
final case class Policy(allow: List[String], deny: Map[String, String], kernel: List[String], host: List[String],
    explicitNulls: Boolean, data: List[String] = Policy.data):

  /** the policy, one line an entry, sorted: what its hash is of */
  def canonical: String = (allow.sorted.map("allow " + _) ++ deny.toList.sorted.map((p, why) => s"deny $p $why") ++
    kernel.sorted.map("kernel " + _) ++ host.sorted.map("host " + _) ++ data.sorted.map("data " + _) :+
    s"explicit-nulls $explicitNulls").mkString("\n")

  def hash: String = java.util.HexFormat.of.formatHex(
    java.security.MessageDigest.getInstance("SHA-256").digest(canonical.getBytes("UTF-8")))

  /** why `path`, of the package `pkg`, may not be named; None when it may. The kernel's packages are allowed, not
   * the packages under them */
  def refused(path: String, pkg: String): Option[String] =
    deny.collectFirst { case (entry, why) if matches(entry, path) => why }
      .orElse(if allow.exists(matches(_, path)) || kernel.contains(pkg) then None
        else Some("it is not on the allowlist"))

  private def matches(entry: String, path: String): Boolean =
    if entry.startsWith("*.") then path.endsWith(entry.drop(1)) || path == entry.drop(2)
    else if entry.contains(".*.") then
      val (prefix, suffix) = entry.splitAt(entry.indexOf(".*."))
      path.startsWith(prefix + ".") && path.endsWith(suffix.drop(2))
    else path == entry || path.startsWith(entry + ".")

  /** `path` is of a type in `data` */
  def isData(path: String): Boolean = data.exists(matches(_, path))

  def isHost(pkg: String): Boolean = host.exists(h => pkg == h || pkg.startsWith(h + "."))
  /** in a package of the kernel or under one: `private[safe]` reaches every package under `okay.safe` */
  def isKernel(pkg: String): Boolean = kernel.exists(k => pkg == k || pkg.startsWith(k + "."))

object Policy:
  /** THE DEFAULT POLICY: what a program over the okay-safe kernel needs of the standard library and the JDK, and
   * nothing that reads the world, writes it, or forges what the types promise */
  val default: Policy = Policy(
    allow = List(
      // the language: the classes of the package `scala` itself are matched by `scala.<Class>`, listed in `direct`
      "scala.collection", "scala.math", "scala.annotation", "scala.compiletime", "scala.deriving",
      "scala.caps", "scala.reflect.Enum", "scala.util.Either", "scala.util.Left", "scala.util.Right",
      "scala.util.hashing", "scala.util.boundary", "scala.util.control.TailCalls", "scala.util.matching",
      "scala.runtime.ScalaRunTime", "scala.runtime.Statics", "scala.runtime.EnumValue", "scala.runtime.Tuples",
      "scala.runtime.TupleXXL", "scala.runtime.BoxesRunTime", "scala.runtime.Rich", "scala.runtime.function",
      "scala.runtime.AbstractFunction", "scala.runtime.AbstractPartialFunction", "scala.runtime.Scala3RunTime",
      "scala.runtime.TupledFunctions", "scala.runtime.FunctionXXL", "scala.runtime.TypeBox",
      "java.lang.Object", "java.io.Serializable", "java.lang.String", "java.lang.Boolean", "java.lang.Byte",
      "java.lang.Character", "java.lang.Short", "java.lang.Integer", "java.lang.Long", "java.lang.Float",
      "java.lang.Double",
      "java.lang.Number", "java.lang.Math", "java.lang.StrictMath", "java.lang.Comparable", "java.lang.CharSequence",
      "java.lang.Enum", "java.lang.Record", "java.lang.Throwable", "java.lang.Exception", "java.lang.RuntimeException",
      "java.lang.IllegalArgumentException", "java.lang.IllegalStateException", "java.lang.IndexOutOfBoundsException",
      "java.lang.UnsupportedOperationException", "java.lang.Class.getName", "java.lang.Class.getSimpleName",
      "java.math", "java.util.function", "java.util.Objects", "java.util.Optional", "java.time") ++ direct,
    deny = Map(
      "scala.Any.asInstanceOf" -> "a cast forges any type, a capability's too",
      "scala.Predef.println" -> "it writes to the console: an ambient effect",
      "scala.Predef.print" -> "it writes to the console: an ambient effect",
      "scala.Predef.printf" -> "it writes to the console: an ambient effect",
      "scala.Predef.???" -> "it throws: failures are an effect (Throws)",
      "scala.Predef.require" -> "it throws: failures are an effect (Throws)",
      "scala.Predef.assert" -> "it throws: failures are an effect (Throws)",
      "scala.Console" -> "the console: an ambient effect",
      "scala.collection.mutable" -> "mutable state outside a handler",
      "scala.collection.concurrent" -> "mutable state outside a handler",
      "scala.caps.unsafe" -> "an escape hatch of capture checking",
      "scala.caps.internal" -> "the capture checker's own",
      "scala.annotation.unchecked" -> "an escape hatch of the checks",
      "scala.unchecked" -> "an escape hatch of the checks",
      "java.lang.Math.random" -> "a random number: an ambient effect (Random)",
      "java.lang.StrictMath.random" -> "a random number: an ambient effect (Random)",
      "java.lang.Integer.getInteger" -> "it reads a system property: an ambient effect",
      "java.lang.Long.getLong" -> "it reads a system property: an ambient effect",
      "java.lang.Boolean.getBoolean" -> "it reads a system property: an ambient effect",
      "java.lang.Object.wait" -> "it blocks on a monitor: threads are the host's",
      "java.lang.Object.notify" -> "it signals a monitor: threads are the host's",
      "java.lang.Object.notifyAll" -> "it signals a monitor: threads are the host's",
      "java.lang.Object.synchronized" -> "a monitor: threads are the host's",
      "java.lang.Object.getClass" -> "the way into reflection",
      "java.time.Clock" -> "the system's clock: an ambient effect (Clock)",
      "java.time.*.now" -> "the time now: an ambient effect (Clock)",
      "scala.util.Properties" -> "it reads the environment and the system properties: an ambient effect",
      "scala.util.PropertiesTrait" -> "it reads the environment and the system properties: an ambient effect",
      "java.time.InstantSource" -> "the system's clock: an ambient effect (Clock)",
      "okay.safe.Runner" -> "the authority to run programs is the host's",
      "okay.safe.run" -> "a program runs no program: the host runs it",
      "okay.safe.step" -> "a program runs no program: the host runs it",
      "okay.safe.Authority" -> "handlers that hold an authority are made at the host's edge",
      "okay.safe.Machine" -> "the machine is the kernel's",
      "okay.safe.Table" -> "a table of resources is the host's: a handler keeps it, a program holds ids"),
    kernel = List("okay.safe", "okay.safe.std"),
    host = Nil,
    explicitNulls = false)

  /** THE DATA OF A LIBRARY that may cross the boundary: immutable values with nothing of the host's in them; the
   * kernel's ids; and the kernel's evidence of rows, which a host gives a program with every call (`using`) */
  lazy val data: List[String] = List("java.time.Instant", "java.time.Duration", "java.time.LocalDate",
    "java.time.LocalDateTime", "java.time.ZoneOffset", "okay.safe.Id", "okay.safe.In", "okay.safe.Split",
    "okay.safe.Sub", "okay.safe.Known", "okay.safe.Plain", "okay.safe.Sensitive")

  /** the classes and objects of the package `scala` itself that a program may name */
  private lazy val direct: List[String] = List("Any", "AnyRef", "AnyVal", "Nothing", "Null", "Unit", "Boolean",
    "Byte", "Short", "Char", "Int", "Long", "Float", "Double", "String", "Predef", "Option", "Some", "None",
    "Product", "Serializable", "Equals", "Tuple", "EmptyTuple", "*:", "NonEmptyTuple", "Function", "PartialFunction",
    "List", "Nil", "::", "Seq", "IndexedSeq", "Iterable", "Iterator", "Vector", "Range", "Ordering", "Ordered",
    "Array", "StringContext", "Enumeration", "Conversion", "CanEqual", "Selectable", "Singleton", "Matchable",
    "LazyList", "BigInt", "BigDecimal", "IllegalArgumentException", "NoSuchElementException", "deprecated",
    "transient", "inline", "throws", "specialized", "volatile", "native", "<repeated>",
    "<byname>", "<FromJavaObject>", "Symbol", "ValueOf", "Mirror", "IArray", "Either", "Left", "Right",
    "TupleXXL", "DummyImplicit", "language", "Dynamic", "NamedTuple", "NamedTupleDecomposition", "PolyFunction")
    .map("scala." + _) ++ (1 to 22).flatMap(n => List(s"scala.Function$n", s"scala.Tuple$n", s"scala.Product$n"))
    ++ List("scala.Function0", "scala.ContextFunction0") ++ (1 to 22).map(n => s"scala.ContextFunction$n")
