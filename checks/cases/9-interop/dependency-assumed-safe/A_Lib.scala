// A dependency compiled outside safe mode, which says of itself that it is safe: safe mode believes it.
package lib // whole: not compiled in safe mode

@caps.assumeSafe // whole: @assumeSafe outside safe mode
object Lib:
  def now(): Long = System.currentTimeMillis() // whole: java.lang.System.currentTimeMillis: it is not on the allowlist
