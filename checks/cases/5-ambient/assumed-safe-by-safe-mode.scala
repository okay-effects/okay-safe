// The ambient effects safe mode lets through: its assumed-safe packages (`scala.util`, `scala.runtime`,
// `java.time`) and classes (`java.lang.Math`, `java.lang.Integer`, `java.lang.Object`) hold them. In a pure
// continuation too: they capture nothing. The whole-program check refuses each.
import language.experimental.safe

object Through:
  val random: Int -> Int = n => scala.util.Random.nextInt(n) // whole: scala.util.Random.nextInt: it is not on the allowlist
  val random2: Int -> Double = n => Math.random() * n // whole: java.lang.Math.random: a random number
  val now: Int -> Long = n => java.time.Instant.now().toEpochMilli + n // whole: java.time.Instant.now: the time now
  val today: Int -> String = n => java.time.LocalDate.now().toString * n // whole: java.time.LocalDate.now: the time now
  val clock: Int -> Long = n => java.time.Clock.systemUTC().millis() + n // whole: java.time.Clock.systemUTC: the system's clock
  val env: Int -> String = n => scala.util.Properties.envOrElse("HOME", "") * n // whole: scala.util.PropertiesTrait.envOrElse: it reads the environment
  val home: Int -> String = n => scala.util.Properties.userHome * n // whole: scala.util.PropertiesTrait.userHome: it reads the environment
  val prop: Int -> Integer = n => Integer.getInteger("x", n) // whole: java.lang.Integer.getInteger: it reads a system property
  val write: Int -> Unit = n => scala.runtime.coverage.Invoker.invoked(n, "/tmp/okay-safe-coverage") // whole: scala.runtime.coverage.Invoker.invoked: it is not on the allowlist
  def block(lock: Object): Unit = lock.wait() // whole: java.lang.Object.wait: it blocks on a monitor
  def monitor(lock: Object): Int = lock.synchronized(1) // whole: java.lang.Object.synchronized: a monitor
