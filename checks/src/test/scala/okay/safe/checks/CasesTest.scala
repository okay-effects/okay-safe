package okay.safe.checks

import java.nio.file.Files

/** every case of every group (checks/cases): what it must not compile, it does not, with the error expected; what
 * compiles and the whole-program check refuses, it refuses, with the finding expected; the rest is clean */
class CasesTest extends munit.FunSuite:
  for group <- Cases.groups; c <- Cases.cases(group) do
    test(s"$group/${c.name}"):
      val out = Files.createTempDirectory("okay-safe-case")
      val expected = c.expected
      val reported = c.compile(out)
      def shown(r: Cases.Reported) = s"${r.file}:${r.line}: ${r.text}"
      val errors = expected.filter(_.kind == "error")
      if errors.nonEmpty then
        for e <- errors do
          assert(reported.exists(r => r.file == e.file && r.line == e.line && r.text.contains(e.text)),
            s"expected at ${e.file}:${e.line}: ${e.text}\nreported:\n${reported.map(shown).mkString("\n")}")
        for r <- reported do
          assert(errors.exists(e => r.file == e.file && r.line == e.line),
            s"unexpected: ${shown(r)}")
      else
        assert(reported.isEmpty, s"expected to compile:\n${reported.map(shown).mkString("\n")}")
        val found = c.whole(out)
        val wholes = expected.filter(_.kind == "whole")
        for e <- wholes do
          assert(found.exists(f => f.file == e.file && f.line == e.line && f.what.contains(e.text)),
            s"expected at ${e.file}:${e.line}: ${e.text}\nfound:\n${found.mkString("\n")}")
        for f <- found do
          assert(wholes.exists(e => f.file == e.file && f.line == e.line && f.what.contains(e.text)),
            s"unexpected finding: $f")
