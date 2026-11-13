// The mutable collections, and the runtime's mutable boxes: not assumed safe, or rejected.
import language.experimental.safe

object Collections:
  val buffer = scala.collection.mutable.ArrayBuffer[Int]() // error: Cannot refer to
  val ref = scala.runtime.IntRef.create(0) // error: Cannot refer to
