// An id of a resource forged by a program: its one maker is the kernel's, and the type is opaque — no number is an
// id. (id-forged-by-a-cast: nor a cast, nor the host's table.)
import language.experimental.safe
import okay.safe.*

object Forge:
  // error: method make cannot be accessed
  def made: Id[List] = Id.make[List](1)
  // error: Found:    (1L : Long)
  def typed: Id[List] = 1L
  def kept(id: Id[List]): (Id[List], Boolean) = (id, id == id)
