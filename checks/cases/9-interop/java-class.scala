// Java classes named in safe mode. `java.util.ArrayList[Int](4)` — a constructor with type arguments — is missed,
// as `HashMap` is (6-global-state); `java.util.stream` is assumed safe. The whole-program check refuses both.
import language.experimental.safe

object Java:
  def list(): Int = java.util.ArrayList[Int](4).size() // whole: java.util.ArrayList
  def stream(): Long = java.util.stream.IntStream.range(0, 3).count() // whole: java.util.stream.IntStream
  def file(): String = java.io.File("x").getName // error: Cannot refer to class File in package java.io
