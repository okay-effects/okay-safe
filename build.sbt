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

/** THE KERNEL: rows, programs as data, handlers, the CEK machine, the audit; the standard effects */
lazy val core = (project in file("core"))
  .settings(name := "okay-safe", scalacOptions ++= strict, libraryDependencies += munit)

lazy val root = (project in file(".")).aggregate(core).settings(publish / skip := true)
