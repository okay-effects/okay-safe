// A program makes the handler that holds an authority: `Authority` is the host's.
import language.experimental.safe
import okay.safe.*
import okay.safe.example.*

object MyOwn:
  def h[A]: Handler[Bank, Pure, A, A] =
    // error: Cannot refer to method tail in object Authority
    Authority.tail[Bank, Pure, A, A](pure)([X] => (op: Bank[X]) => ???)
