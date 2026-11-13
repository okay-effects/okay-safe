package okay.safe

import language.experimental.safe
import language.experimental.separationChecking

/** AN ID OF A RESOURCE A HANDLER HOLDS: a file, a session, a connection — what a program opens and uses but must
 * not hold. The resource stays with the handler, in its `Table` (Unchecked.scala, the host's); the program is given
 * this, and gives it back: `Open(…) extends Files[Id[Files]]`, `Read(file: Id[Files]) extends Files[String]`,
 * `Close(file: Id[Files])`. `K` says whose: an effect, a class — any kind.
 *
 * It CANNOT BE FORGED: the type is opaque, its one maker is the kernel's (`private[safe]`, called by `Table`), and a
 * program can neither cast nor declare into the kernel's package (the whole-program check). A program may keep an
 * id, pass it, compare it; one that was closed, or is another table's, is refused by the handler that is given it:
 * ids are unique in a JVM, so an id is in one table at most. Data: what crosses the boundary (docs/research.md). */
opaque type Id[K <: AnyKind] = Long

object Id:
  /** the id numbered `n`: the kernel's, for `Table` */
  private[safe] def make[K <: AnyKind](n: Long): Id[K] = n

  extension [K <: AnyKind](id: Id[K])
    /** its number: the kernel's, for `Table` */
    private[safe] def number: Long = id
