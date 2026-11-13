package okay.safe.whole

import java.nio.file.{Files, Path}
import java.util.zip.ZipFile

import scala.jdk.CollectionConverters.*
import scala.quoted.*
import scala.tasty.inspector.*

import dotty.tools.dotc.core.tasty.{DottyUnpickler, TastyUnpickler}

/** a finding: where, and what */
final case class Finding(file: String, line: Int, what: String):
  override def toString: String = s"$file:$line: $what"

/*
 * THE WHOLE-PROGRAM CHECK. Safe mode trusts what it is told: the packages it assumes safe (all of `scala.util`,
 * `scala.runtime`, `java.time`), and any class that says `@assumeSafe`, wherever it was compiled. This check trusts
 * nothing of the program: it reads the TASTy of every class of the program — its own and every dependency given
 * with it, so transitively — and holds every reference it makes outside them to the policy (Policy.scala).
 *
 * Of each program unit (a unit not in a host package) it asks:
 *   - compiled in safe mode: the file imports `language.experimental.safe`; and capture checked, by its TASTy's
 *     attributes; with explicit nulls when the policy asks it
 *   - every symbol it names outside the program allowed by the policy; none of the host's
 *   - no `throw`, no `try`: a failure is an effect (`Throws`)
 *   - no class declared into a package of the kernel: package-private access is the kernel's
 * and of every unit, the host's too: no `@assumeSafe` outside safe mode — a claim nobody checked.
 * Code the compiler made (`Synthetic`: a case class's `productElement`, an enum's `fromOrdinal`) is not read.
 */
object Whole:

  /** the findings of the program at `program` (directories and jars of TASTy, all inspected), its trusted
   * dependencies on `classpath` (the kernel, the standard library: read for their types, not inspected) */
  def check(program: List[Path], classpath: List[Path], policy: Policy): List[Finding] =
    val (dirs, jars) = program.partition(Files.isDirectory(_))
    val tastyFiles = dirs.flatMap(d => Files.walk(d).iterator.asScala.filter(_.toString.endsWith(".tasty")).toList)
    val attributes = tastyFiles.map(f => f.toString -> attributesOf(Files.readAllBytes(f))).toMap ++
      jars.flatMap(jarAttributes)
    val inspector = Reader(policy, attributes)
    val deps = (classpath ++ program).map(_.toString)
    if tastyFiles.nonEmpty || jars.nonEmpty then
      TastyInspector.inspectAllTastyFiles(tastyFiles.map(_.toString), jars.map(_.toString), deps)(inspector): Unit
    inspector.findings.toList.distinct.sortBy(f => (f.file, f.line, f.what))

  /** (captureChecked, explicitNulls) of a TASTy file */
  private def attributesOf(bytes: Array[Byte]): (Boolean, Boolean) =
    TastyUnpickler(bytes).unpickle(new DottyUnpickler.AttributesSectionUnpickler) match
      case Some(u) => (u.attributes.captureChecked, u.attributes.explicitNulls)
      case None => (false, false)

  private def jarAttributes(jar: Path): List[(String, (Boolean, Boolean))] =
    val zip = ZipFile(jar.toFile)
    try zip.entries.asScala.filter(_.getName.endsWith(".tasty")).map(e =>
      s"$jar!/${e.getName}" -> attributesOf(zip.getInputStream(e).readAllBytes)).toList
    finally zip.close()

  private final class Reader(policy: Policy, attributes: Map[String, (Boolean, Boolean)]) extends Inspector:
    val findings = scala.collection.mutable.ListBuffer[Finding]()

    def inspect(using q: Quotes)(tastys: List[Tasty[q.type]]): Unit =
      import q.reflect.*

      def names(s: Symbol): List[String] =
        if s.isNoSymbol || s.name == "<root>" || s.name == "_root_" then Nil
        else
          val n = s.name.stripSuffix("$")
          if n.endsWith("$package") || n == "package" then names(s.owner) else n :: names(s.owner)
      def path(s: Symbol): String = names(s).reverse.mkString(".")
      def pkg(s: Symbol): Symbol = if s.isNoSymbol || s.isPackageDef then s else pkg(s.owner)
      def topLevel(s: Symbol): Symbol =
        if s.isNoSymbol || s.owner.isNoSymbol || s.owner.isPackageDef then s else topLevel(s.owner)

      /** the top-level classes of the program, by path */
      val own: Set[String] = tastys.flatMap { t =>
        def tops(tree: Tree): List[String] = tree match
          case PackageClause(_, stats) => stats.flatMap(tops)
          case c: ClassDef => List(path(c.symbol))
          case _ => Nil
        tops(t.ast)
      }.toSet

      for tasty <- tastys do
        val file = tasty.path
        def report(pos: Position, what: String): Unit =
          findings += Finding(pos.sourceFile.name, pos.startLine + 1, what)
        val unitPkg = tasty.ast match
          case PackageClause(pid, _) => path(pid.symbol)
          case _ => ""
        val isHost = policy.isHost(unitPkg)

        // safe mode, capture checking, explicit nulls
        def safeImport(tree: Tree): Boolean = tree match
          case PackageClause(_, stats) => stats.exists(safeImport)
          case Import(expr, selectors) =>
            path(expr.symbol) == "scala.language.experimental" &&
              selectors.exists { case SimpleSelector(n) => n == "safe"; case _ => false }
          case _ => false
        val (cc, nulls) = attributes.getOrElse(file, (false, false))
        val pos = tasty.ast.pos
        if !isHost then
          if !safeImport(tasty.ast) then report(pos, "not compiled in safe mode: no `import language.experimental.safe`")
          if !cc then report(pos, "not capture checked")
          if policy.explicitNulls && !nulls then report(pos, "not compiled with explicit nulls (-Yexplicit-nulls)")
          if policy.isKernel(unitPkg) then
            report(pos, s"declares into the kernel's package $unitPkg: its package-private parts are open there")

        def named(sym: Symbol, at: Position): Unit =
          if !sym.isNoSymbol && !sym.isPackageDef then
            val top = topLevel(sym)
            if !isHost && policy.isHost(path(pkg(top))) then report(at, s"a program names the host: ${path(sym)}")
            else if !own(path(top)) && !isHost then
              policy.refused(path(sym), path(pkg(top))).foreach(why => report(at, s"${path(sym)}: $why"))

        object walk extends TreeTraverser:
          override def traverseTree(tree: Tree)(owner: Symbol): Unit = tree match
            case d: Definition if d.symbol.flags.is(Flags.Synthetic) && !d.symbol.isAnonymousFunction => ()
            case d: Definition =>
              d.symbol.annotations.foreach { a =>
                if path(a.tpe.typeSymbol) == "scala.caps.assumeSafe" && !safeImport(tasty.ast) then
                  report(d.pos, s"@assumeSafe outside safe mode: ${path(d.symbol)} claims what nobody checked")
              }
              traverseTreeChildren(tree)(owner)
            case _: Import | _: Export => ()
            case Inlined(call, bindings, _) =>
              call.foreach(traverseTree(_)(owner))
              bindings.foreach(traverseTree(_)(owner))
            case i: Ident if i.symbol.isTerm => named(i.symbol, i.pos)
            case s @ Select(qual, _) =>
              if s.symbol.isTerm && s.name != "<init>" then named(s.symbol, s.pos)
              traverseTree(qual)(owner)
            case n @ New(tpt) => named(tpt.tpe.typeSymbol, n.pos)
            case t: Try if !isHost =>
              report(t.pos, "try: an exception caught is an effect unsaid; a failure is an effect (Throws)")
              traverseTreeChildren(tree)(owner)
            case a @ Apply(fun, _) if !isHost && fun.symbol.name == "throw" && path(fun.symbol.owner) == "" =>
              report(a.pos, "throw: an exception is an effect unsaid; a failure is an effect (Throws)")
              traverseTreeChildren(tree)(owner)
            case _ => traverseTreeChildren(tree)(owner)
        walk.traverseTree(tasty.ast)(Symbol.spliceOwner)
