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
 *   - no class declared into a package of the kernel or under it: package-private access is the kernel's
 *   - THE BOUNDARY RULE: every type it holds allowed — none of the host's, no client of a library —, and every
 *     answer of its effects first-order data (below, at `held` and `data`)
 * and of every unit, the host's too: no `@assumeSafe` outside safe mode — a claim nobody checked —, and no class in
 * the kernel's packages (a host under `okay.safe` could make a runner); of the host's: what it gives a function or
 * a class of the program is first-order data.
 * Code the compiler made (`Synthetic`: a case class's `productElement`, an enum's `fromOrdinal`) is not read; the
 * classes it made are (an enum's companion holds its cases).
 */
object Whole:
  /** the check's version, recorded in a seal: raised when what it finds changes */
  val version = "okay-safe-whole 3"

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
      /** the annotations the compiler puts on the types it infers */
      def internal(p: String): Boolean =
        p.startsWith("scala.caps.internal.") || p.startsWith("scala.annotation.internal.")
      /** what the compiler puts on a type it infers, beside those: a stable path, a variance it has checked */
      def insertedInInferred(p: String): Boolean =
        p == "scala.annotation.unchecked.uncheckedStable" || p == "scala.annotation.unchecked.uncheckedVariance"
      def inferred(t: Tree): Boolean = t match
        case Inferred() => true
        case _ => false
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

      def report(pos: Position, what: String): Unit =
        findings += Finding(pos.sourceFile.name, pos.startLine + 1, what)
      def hostSymbol(s: Symbol): Boolean = policy.isHost(path(pkg(topLevel(s))))
      /** of the program: one of its own classes, not the host's */
      def programSymbol(s: Symbol): Boolean = !s.isNoSymbol && own(path(topLevel(s))) && !hostSymbol(s)
      def kernelSymbol(s: Symbol): Boolean = policy.isKernel(path(pkg(topLevel(s))))
      /** a type alias, to follow; an opaque type is a type of its own */
      def alias(s: Symbol): Boolean = s.isAliasType && !s.flags.is(Flags.Opaque)
      def shown(t: TypeRepr): String = t.show(using Printer.TypeReprShortCode)

      /*
       * THE BOUNDARY RULE (docs/research.md, "the boundary"). Real resources live in handlers only, captured there by
       * the host; a program holds data, effects, and ids. Two checks of types, beside the check of names:
       *   - HELD: every type a program unit declares, infers, tests in a pattern or gives as a type argument, and the
       *     type of every reference it makes, is decomposed — applied types, tuples, unions and intersections,
       *     refinements, functions, methods — and every class in it is the program's own, the kernel's, a type the
       *     policy allows a program to name, or data. A client of the JDK's, a class of the host's: a finding.
       *   - DATA, where values cross from the host into a program: an effect's answer (`extends Bank[X]`, a
       *     `Word[E, X]`), and every argument a host unit gives a function or a class of the program. There only
       *     first-order data: no function — a host's lambda capturing a client the capture checker does not track is
       *     IO in disguise —, no `Any`, no class a host may subclass.
       */

      /** where the effect is in the kernel's types: `Word[E, X]`, `In[E, R]`, `R + E` (`Sum[R, E, X]`), ... */
      val effectAt: Map[String, Int] = Map("okay.safe.Word" -> 0, "okay.safe.In" -> 0, "okay.safe.Split" -> 0,
        "okay.safe.Handler" -> 0, "okay.safe.Interceptor" -> 0, "okay.safe.Sensitive" -> 0, "okay.safe.Plain" -> 0,
        "okay.safe.Known" -> 0, "okay.safe.Pass" -> 0, "okay.safe.Performing" -> 0, "okay.safe.Audited.Op" -> 0,
        "okay.safe.Sum" -> 1, "okay.safe.Comp.Ask" -> 1, "okay.safe.Step.Asked" -> 1)
      /** the effects the program's types name, by path: whose operations' answers are held to the rule of data */
      val effects = scala.collection.mutable.Set[String]()
      def effectOf(t: TypeRepr): Option[Symbol] = t.dealias match
        case TypeLambda(_, _, res) => effectOf(res)
        case AppliedType(tycon, _) => effectOf(tycon)
        case r: TypeRef if r.typeSymbol.isClassDef || r.typeSymbol.flags.is(Flags.Opaque) => Some(r.typeSymbol)
        case _ => None

      /** values: primitives, strings, numbers; options, eithers, tuples and the immutable collections, of data */
      def builtinData(p: String): Boolean =
        Set("scala.Int", "scala.Long", "scala.Short", "scala.Byte", "scala.Char", "scala.Boolean", "scala.Float",
          "scala.Double", "scala.Unit", "scala.Nothing", "java.lang.String", "scala.math.BigInt",
          "scala.math.BigDecimal", "scala.Option", "scala.Some", "scala.None", "scala.util.Either", "scala.util.Left",
          "scala.util.Right", "scala.Tuple", "scala.EmptyTuple", "scala.*:", "scala.NonEmptyTuple")(p) ||
          p.matches("""scala\.Tuple\d+""") ||
          p.startsWith("scala.collection.immutable.") && !p.startsWith("scala.collection.immutable.LazyList") &&
            !p.startsWith("scala.collection.immutable.Stream")
      /** the types that say nothing of what a value is */
      def anything(p: String): Boolean = Set("scala.Any", "scala.AnyRef", "java.lang.Object", "scala.Matchable",
        "scala.AnyVal", "scala.Product", "scala.Equals", "scala.Serializable", "java.io.Serializable")(p)
      def function(t: TypeRepr): Boolean =
        t.isFunctionType || t.isContextFunctionType || t.isDependentFunctionType ||
          Set("scala.PolyFunction", "scala.PartialFunction")(path(t.typeSymbol))
      def higherKinded(t: TypeRepr): Boolean = t.dealias match
        case _: TypeLambda => true
        case r: TypeRef => r.typeSymbol.isClassDef && r.typeSymbol.declaredTypes.exists(_.isTypeParam)
        case _ => false

      /** why a value of type `t` is not first-order data; None when it is. A type parameter is: what it stands for
       * is held to the rule where it is given */
      def data(t: TypeRepr, seen: Set[Symbol] = Set.empty): Option[String] = t match
        case AnnotatedType(u, _) => data(u, seen)
        case ConstantType(_) => None
        case r: TermRef => data(r.widen, seen)
        case ByNameType(u) => data(u, seen)
        case OrType(a, b) => data(a, seen).orElse(data(b, seen))
        case AndType(a, b) => if data(a, seen).isEmpty || data(b, seen).isEmpty then None else data(a, seen)
        case _ if function(t) => Some(s"a function, ${shown(t)}: code of whoever made it — a host's lambda is IO " +
          "in disguise, with what it captured (a client capture checking does not track)")
        case Refinement(parent, _, _) => data(parent, seen)
        case AppliedType(tycon, args) =>
          if t.dealias != t then data(t.dealias, seen)
          else data(tycon, seen).orElse(args.filterNot(higherKinded).iterator.flatMap(data(_, seen)).nextOption())
        case TypeLambda(_, _, res) => data(res, seen)
        case r: TypeRef =>
          val s = r.typeSymbol
          if alias(s) then data(r.dealias, seen)
          else if s.flags.is(Flags.Opaque) then
            if policy.isData(path(s)) then None else data(r.translucentSuperType, seen)
          else if s.isClassDef then dataClass(s, seen)
          else None
        case _: ParamRef => None
        case _ => Some(s"${shown(t)} is not data")

      def dataClass(s: Symbol, seen: Set[Symbol]): Option[String] =
        val p = path(s)
        val f = s.flags
        if seen(s) || builtinData(p) || policy.isData(p) then None
        else if anything(p) then Some(s"$p says nothing of what a value is: its toString, equals and hashCode are " +
          "the value's own — a host's, run by the program")
        else if hostSymbol(s) then Some(s"$p is the host's")
        else if !programSymbol(s) && !kernelSymbol(s) then Some(s"$p is not data: a class of a library")
        else if !(f.is(Flags.Final) || f.is(Flags.Sealed) || f.is(Flags.Module) || f.is(Flags.Enum)) then
          Some(s"$p is neither final nor sealed: a subclass of the host's may override any of it")
        else
          val fields = s.declaredFields
          fields.find(_.flags.is(Flags.Mutable)).map(v => s"$p has a var, ${v.name}")
            .orElse(fields.iterator.flatMap(v =>
              data(s.typeRef.memberType(v), seen + s).map(why => s"$p.${v.name}: $why")).nextOption())
            .orElse(s.children.filter(_.isClassDef).iterator.flatMap(c => dataClass(c, seen + s)).nextOption())

      /** what is wrong with a program holding a value of type `t`: a class of the host's, one not allowlisted; and
       * with `answers`, a word whose answer is not data */
      val heldSeen = scala.collection.mutable.HashMap[(TypeRepr, Boolean), List[String]]()
      def held(t: TypeRepr, answers: Boolean): List[String] = heldSeen.getOrElseUpdate((t, answers), t match
        case AnnotatedType(u, _) => held(u, answers)
        case AndType(a, b) => held(a, answers) ++ held(b, answers)
        case OrType(a, b) => held(a, answers) ++ held(b, answers)
        case _: AppliedType if t.dealias != t => held(t.dealias, answers)
        case AppliedType(tycon, args) =>
          val tyconPath = path(tycon.typeSymbol)
          effectAt.get(tyconPath).flatMap(args.lift).flatMap(effectOf).foreach(e => effects += path(e))
          val answer = if answers && tyconPath == "okay.safe.Word" then
            args.lift(1).flatMap(data(_)).map(why => s"an effect's answer must be first-order data: $why").toList
          else Nil
          held(tycon, answers) ++ args.flatMap(held(_, answers)) ++ answer
        case Refinement(parent, _, info) => held(parent, answers) ++ held(info, answers)
        case MethodType(_, params, res) => params.flatMap(held(_, answers)) ++ held(res, answers)
        case PolyType(_, _, res) => held(res, answers)
        case TypeLambda(_, _, res) => held(res, answers)
        case TypeBounds(lo, hi) => held(lo, answers) ++ held(hi, answers)
        case ByNameType(u) => held(u, answers)
        case r: TermRef => if r.termSymbol.isPackageDef then Nil else held(r.widen, answers)
        case r: TypeRef =>
          val s = r.typeSymbol
          if s.isNoSymbol || s.isPackageDef then Nil
          else if alias(s) then held(r.dealias, answers)
          else if !(s.isClassDef || s.flags.is(Flags.Opaque)) then Nil
          else if hostSymbol(s) then List(s"a program names the host's type ${path(s)}")
          else if programSymbol(s) || policy.isData(path(s)) then Nil
          else policy.refused(path(s), path(pkg(topLevel(s)))).map(why => s"the type ${path(s)}: $why").toList
        case _ => Nil)

      /** the classes of the program that extend an effect: the place, the class, the effect and the answer — held
       * to the rule of data once every unit has said which types are effects */
      val operations = scala.collection.mutable.ListBuffer[(Position, String, String, TypeRepr)]()

      for tasty <- tastys do
        val file = tasty.path
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
          if !safeImport(tasty.ast) then
            report(pos, "not compiled in safe mode: no `import language.experimental.safe`")
          if !cc then report(pos, "not capture checked")
          if policy.explicitNulls && !nulls then report(pos, "not compiled with explicit nulls (-Yexplicit-nulls)")
        // every package clause of the unit, the nested ones too: `package a.b { ... }` inside a file of another
        def packages(tree: Tree): List[(String, Position)] = tree match
          case p @ PackageClause(pid, stats) => (path(pid.symbol), p.pos) :: stats.flatMap(packages)
          case _ => Nil
        for (p, at) <- packages(tasty.ast) if policy.isKernel(p) do
          report(at, s"declares into the kernel's package $p: its package-private parts are open there")

        def named(sym: Symbol, at: Position): Unit =
          if !sym.isNoSymbol && !sym.isPackageDef then
            val top = topLevel(sym)
            if !isHost && policy.isHost(path(pkg(top))) then report(at, s"a program names the host: ${path(sym)}")
            else if !own(path(top)) && !isHost then
              policy.refused(path(sym), path(pkg(top))).foreach(why => report(at, s"${path(sym)}: $why"))

        /** the boundary rule of a program unit: what it holds (`answers`: and what its words are answered) */
        def holds(t: TypeRepr, at: Position, answers: Boolean): Unit =
          if !isHost then held(t, answers).foreach(report(at, _))

        object walk extends TreeTraverser:
          override def traverseTree(tree: Tree)(owner: Symbol): Unit = tree match
            case d: Definition if d.symbol.flags.is(Flags.Synthetic) && !d.symbol.isAnonymousFunction &&
                !d.symbol.isClassDef => ()
            case d: Definition =>
              d.symbol.annotations.foreach { a =>
                if path(a.tpe.typeSymbol) == "scala.caps.assumeSafe" && !safeImport(tasty.ast) then
                  report(d.pos, s"@assumeSafe outside safe mode: ${path(d.symbol)} claims what nobody checked")
              }
              d match
                case c: ClassDef if !isHost =>
                  c.parents.foreach { p =>
                    val tpe = p match
                      case t: TypeTree => t.tpe
                      case t: Term => t.tpe
                      case _ => TypeRepr.of[Any]
                    tpe.dealias match
                      case AppliedType(tycon, args) if args.nonEmpty =>
                        operations += ((c.pos, c.name, path(tycon.typeSymbol), args.last))
                      case _ => ()
                  }
                case _ => ()
              traverseTreeChildren(tree)(owner)
            case _: Import | _: Export => ()
            case Inlined(call, bindings, _) =>
              call.foreach(traverseTree(_)(owner))
              bindings.foreach(traverseTree(_)(owner))
            case i: Ident if i.symbol.isTerm =>
              named(i.symbol, i.pos)
              holds(i.tpe.widen, i.pos, answers = false)
            case s @ Select(qual, _) =>
              if s.symbol.isTerm && s.name != "<init>" then
                named(s.symbol, s.pos)
                holds(s.tpe.widen, s.pos, answers = false)
              traverseTree(qual)(owner)
            case n @ New(tpt) =>
              named(tpt.tpe.typeSymbol, n.pos)
              holds(tpt.tpe, n.pos, answers = false)
            case t: TypeTree =>
              def annotations(tpe: TypeRepr): Unit = tpe match
                case AnnotatedType(under, annot) =>
                  val sym = annot.tpe.typeSymbol
                  if !internal(path(sym)) && !(inferred(t) && insertedInInferred(path(sym))) then named(sym, t.pos)
                  annotations(under)
                case _ => ()
              annotations(t.tpe)
              holds(t.tpe, t.pos, answers = true)
              traverseTreeChildren(tree)(owner)
            case t: Try if !isHost =>
              report(t.pos, "try: an exception caught is an effect unsaid; a failure is an effect (Throws)")
              traverseTreeChildren(tree)(owner)
            // `throw` is a method of the compiler's own `<special-ops>`
            case Apply(fun, args) if fun.symbol.name == "throw" && path(fun.symbol.owner) == "<special-ops>" =>
              if !isHost then
                report(tree.pos, "throw: an exception is an effect unsaid; a failure is an effect (Throws)")
              args.foreach(traverseTree(_)(owner))
            // THE HOST GIVES A PROGRAM: data only, its arguments' types and the types it instantiates
            case Apply(fun, args) if isHost && programSymbol(fun.symbol) =>
              for a <- args; why <- data(a.tpe.widen) do
                report(a.pos, s"the host gives the program's ${path(fun.symbol)} what is not data: $why")
              traverseTreeChildren(tree)(owner)
            case TypeApply(fun, targs) if isHost && programSymbol(fun.symbol) =>
              for a <- targs if !higherKinded(a.tpe); why <- data(a.tpe) do
                report(a.pos, s"the host gives the program's ${path(fun.symbol)} a type that is not data: $why")
              traverseTreeChildren(tree)(owner)
            // an application's type: the effects its words are of, and their answers (`State.get[() => String]`)
            case t: Apply =>
              holds(t.tpe, t.pos, answers = true)
              traverseTreeChildren(tree)(owner)
            case t: TypeApply =>
              holds(t.tpe, t.pos, answers = true)
              traverseTreeChildren(tree)(owner)
            case _ => traverseTreeChildren(tree)(owner)
        walk.traverseTree(tasty.ast)(Symbol.spliceOwner)

      // the answers of the program's operations: of each of its classes that extends an effect
      for (at, name, effect, answer) <- operations if effects(effect); why <- data(answer) do
        report(at, s"the answer of $name, an operation of $effect, must be first-order data: $why")
