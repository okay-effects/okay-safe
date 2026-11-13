//> host: host
package host

import okay.safe.*

object App extends Entry:
  def run(args: List[String])(using Runner): Unit =
    // whole: the host gives the program's prog.Show.show what is not data: prog.Account is neither final nor sealed
    println(okay.safe.run(prog.Show.show(prog.Account("ann"))))
    println(okay.safe.run(prog.Show.shut(prog.Closed("bob"))))
