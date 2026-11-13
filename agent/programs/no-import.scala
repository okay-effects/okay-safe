// refused: neither compiled in safe mode
// The program without `import language.experimental.safe`: it compiles, and none of safe mode's checks ran. The
// gate's own file, in safe mode, cannot even name its program; were it named by no safe code, the whole-program
// check reads the missing import from its TASTy (SealTest).
import okay.safe.*
import okay.bank.Bank
import okay.agent.*

object Task:
  val program: String ! Tools = Bank.balance("company").map(n => s"$n")
