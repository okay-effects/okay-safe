// A field read before it is initialized: with -Wsafe-init and -Werror, a compile error. (The objects' own order is
// checked by -Ysafe-init-global, which this build does not use.)
//> flags: -Wsafe-init -Werror
import language.experimental.safe

class Early:
  val doubled: Int = base * 2
  val base: Int = 21 // error: Access non-initialized value base
