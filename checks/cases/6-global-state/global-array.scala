// A global array: an array is mutable for separation checking; a pure function that writes it captures it.
import language.experimental.safe

object Arrays: // error: object Arrays needs to extend Capability since it has a field `cells` with `any` in its type
  val cells: Array[Int]^ = Array(0)
  // error: Note that capability `Arrays.cells` cannot flow into capture set {}
  val write: Int -> Unit = x => cells(0) = x
