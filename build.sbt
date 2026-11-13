// okay-safe: a research kernel of algebraic effects, the okay-effects model, made as safe as Scala 3 can make it
// (capture checking, safe mode, separation checking, and a whole-program check of the TASTy). docs/research.md

/** THE COMPILER: the newest nightly of Scala 3 when this was written (2026-11-13), pinned. The newest release,
 * 3.10.1, has the same experimental features (safe mode, capture checking, separation checking, uses clauses):
 * `sbt -Dscala=3.10.1 test` builds and checks everything with it too. */
val nightly = "3.10.2-RC1-bin-20261113-2905165-NIGHTLY"
val scala3 = sys.props.getOrElse("scala", nightly)

ThisBuild / organization := "io.github.okay-effects"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := scala3
ThisBuild / resolvers += "scala nightlies" at "https://repo.scala-lang.org/artifactory/maven-nightlies"
ThisBuild / licenses := List("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0.txt"))

/** every lint, every warning an error, and the experimental features allowed in this release */
val strict = Seq("-experimental", "-deprecation", "-feature", "-unchecked", "-Werror", "-Wunused:all",
  "-Wvalue-discard", "-Wnonunit-statement", "-Wimplausible-patterns", "-Wsafe-init")

val munit = "org.scalameta" %% "munit" % "1.3.6" % Test

/** munit registers its tests by lambdas that close over the suite while it is built: -Wsafe-init warns of each */
val tests = Test / scalacOptions -= "-Wsafe-init"

/** THE KERNEL: rows, programs as data, handlers, the CEK machine, the audit; the standard effects */
lazy val core = (project in file("core"))
  .settings(name := "okay-safe", scalacOptions ++= strict, libraryDependencies += munit, tests)

lazy val root = (project in file(".")).aggregate(core, example, whole, gate, agent, checks, bench)
  .settings(publish / skip := true)

/** AN APPLICATION: a bank's programs in safe mode, its client and handler at the host's edge */
lazy val example = (project in file("example")).dependsOn(core)
  .settings(name := "okay-safe-example", scalacOptions ++= strict, publish / skip := true)

/** THE WHOLE-PROGRAM CHECK: every TASTy file of a program and its dependencies read, every reference held to an
 * allowlist — what safe mode assumes of the standard library and of `@assumeSafe`, checked again, transitively */
lazy val whole = (project in file("whole"))
  .settings(name := "okay-safe-whole", scalacOptions ++= strict,
    libraryDependencies ++= Seq("org.scala-lang" %% "scala3-tasty-inspector" % scala3, munit), tests)

/** THE GATE: sources compiled with the required flags, held to the whole-program check, and sealed — a deployment
 * the kernel's Launch verifies before it gives the application a runner */
lazy val gate = (project in file("gate")).dependsOn(whole)
  .settings(name := "okay-safe-gate", scalacOptions ++= strict, tests,
    libraryDependencies ++= Seq("org.scala-lang" %% "scala3-compiler" % scala3, munit))

/** THE GUARANTEES, TESTED: code that must not compile (each with the error expected), code that must, code that
 * compiles and the whole-program check refuses; and the checks of last resort, at run time */
lazy val checks = (project in file("checks")).dependsOn(core, example, whole, gate)
  .settings(name := "okay-safe-checks", publish / skip := true, scalacOptions ++= strict, tests,
    libraryDependencies ++= Seq(munit, "org.scala-lang" %% "scala3-compiler" % scala3 % Test),
    Test / fork := true,
    Test / javaOptions ++= {
      val cp = ((example / Compile / fullClasspath).value.files ++ (core / Compile / fullClasspath).value.files)
        .distinct.map(_.getAbsolutePath).mkString(java.io.File.pathSeparator)
      Seq(s"-Dokay.safe.classpath=$cp", s"-Dokay.safe.cases=${(baseDirectory.value / "cases").getAbsolutePath}",
        s"-Dokay.safe.example=${(example / baseDirectory).value / "src" / "main" / "scala"}")
    })

/** THE BENCHMARK, against okay-scala 0.1.1 from its Pages repository: `sbt bench/run`, and `inside` at -Xss512k */
lazy val bench = (project in file("bench")).dependsOn(core)
  .settings(name := "okay-safe-bench", publish / skip := true, scalacOptions ++= strict,
    resolvers += "okay-scala releases" at "https://okay-effects.github.io/okay-scala/maven",
    libraryDependencies += "io.github.okay-effects" %% "okay-scala" % "0.1.1",
    Compile / run / mainClass := Some("okay.safe.Launch"), run / fork := true,
    run / javaOptions ++= Seq("-Xmx2g", "-Dokay.safe.unsealed=the benchmark",
      "-Dokay.safe.entry=okay.safe.bench.Bench"))

/** THE GATE FOR AGENTS: a program an agent wrote over a log, the clock and the bank, compiled in safe mode, checked
 * whole, and only then run with the host's handlers — its bank behind an audit that asks the guard, the policy a
 * handler. `sbt "agent/run agent/programs/good.scala"` */
lazy val agent = (project in file("agent")).dependsOn(core, example, gate)
  .settings(name := "okay-safe-agent", publish / skip := true, scalacOptions ++= strict, tests,
    libraryDependencies += munit, Test / fork := true,
    Test / javaOptions += s"-Dokay.agent.programs=${(baseDirectory.value / "programs").getAbsolutePath}",
    Compile / run / mainClass := Some("okay.safe.Launch"), run / fork := true,
    run / javaOptions ++= Seq("-Dokay.safe.unsealed=the agents' gate, run from sbt",
      "-Dokay.safe.entry=okay.agent.host.Cli"),
    run / baseDirectory := (LocalRootProject / baseDirectory).value)
