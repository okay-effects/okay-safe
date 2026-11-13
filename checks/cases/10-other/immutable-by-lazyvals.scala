// `scala.runtime.LazyVals` — in a package safe mode assumes safe — writes a field of an immutable value: here the
// tail of a `List`, which a program shares with every path of a multi-shot continuation. Purity is gone, and safe
// mode and capture checking let it through; the whole-program check does not (RuntimeTest runs it).
import language.experimental.safe

object Rewrite:
  def rewrite(xs: List[Int], tail: List[Int]): Boolean =
    val offset = scala.runtime.LazyVals.getOffset(classOf[::[?]], "next") // whole: scala.runtime.LazyVals.getOffset: it is not on the allowlist
    scala.runtime.LazyVals.objCAS(xs, offset, xs.tail, tail) // whole: scala.runtime.LazyVals.objCAS: it is not on the allowlist
