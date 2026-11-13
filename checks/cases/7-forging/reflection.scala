// Reflection, and `sun.misc.Unsafe`: refused.
import language.experimental.safe
import okay.safe.*

object Reflect:
  def field(): Unit = classOf[Cell[?]].getDeclaredField("state").setAccessible(true) // error: Cannot refer to method getDeclaredField in class Class
  def byName(): Unit = { val _ = Class.forName("okay.safe.Machine") } // error: Cannot refer to method forName in object Class
  def methods(x: Object): Int = x.getClass.getMethods.length // error: Cannot refer to method getMethods in class Class
  def unsafe(): Unit = { val _ = sun.misc.Unsafe.getUnsafe } // error: Cannot refer to
