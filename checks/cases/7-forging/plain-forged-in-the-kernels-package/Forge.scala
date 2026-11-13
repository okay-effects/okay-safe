// A program that says it is in the kernel's package forges `Plain[Pay]`, the evidence that an effect is not
// sensitive, and handles a sensitive effect with no audit. It compiles — package-private access is the package's —;
// the whole-program check refuses the package.
package okay.safe // whole: declares into the kernel's package okay.safe

import language.experimental.safe

enum Pay[X]:
  case Transfer(amount: Long) extends Pay[Unit]

object Pay:
  given Sensitive[Pay] = Sensitive()

object Forge:
  def silent[A](p: A ! Pay): A ! Pure =
    handle(p, Handler.tail[Pay, Pure, A, A](pure)([X] => (op: Pay[X]) => op match
      case Pay.Transfer(_) => ()))(using summon[Split[Pay, Pay, Pure]])(using Plain.checked[Pay])
