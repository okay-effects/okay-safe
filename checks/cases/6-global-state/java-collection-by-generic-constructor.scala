// A Java mutable collection, made by a constructor with type arguments: safe mode refuses `java.io.File("...")`, and
// misses `java.util.HashMap[String, Int]()` — the class is named in an inferred type tree, which its check does
// not read. Its methods are members of an instance, which safe mode takes as checked, and capture checking does not
// track a Java object: a global map, written from a pure function. The whole-program check refuses both.
import language.experimental.safe

object Global:
  val counts = java.util.HashMap[String, Integer]() // whole: java.util.HashMap: it is not on the allowlist
  // whole: java.util.HashMap.merge: it is not on the allowlist
  val count: String -> Int = key => counts.merge(key, 1, (a, b) => a + b)
