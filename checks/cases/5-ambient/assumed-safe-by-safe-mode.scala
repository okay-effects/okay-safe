// The ambient effects safe mode lets through: its assumed-safe packages (`scala.util`, `scala.runtime`,
// `java.time`) and classes (`java.lang.Math`, `java.lang.Integer`, `java.lang.Object`) hold them. In a pure
// continuation too: they capture nothing. The whole-program check refuses each.
import language.experimental.safe

object Through:
  // whole: scala.util.Random.nextInt: fresh: a random number
  val random: Int -> Int = n => scala.util.Random.nextInt(n)
  val random2: Int -> Double = n => Math.random() * n // whole: java.lang.Math.random: fresh: a random number
  // whole: java.time.Instant.now: clock: the time now
  val now: Int -> Long = n => java.time.Instant.now().toEpochMilli + n
  // whole: java.time.LocalDate.now: clock: the time now
  val today: Int -> String = n => java.time.LocalDate.now().toString * n
  // whole: java.time.Clock.systemUTC: clock: the time now
  val clock: Int -> Long = n => java.time.Clock.systemUTC().millis() + n
  // whole: scala.util.PropertiesTrait.envOrElse: reader: the environment
  val env: Int -> String = n => scala.util.Properties.envOrElse("HOME", "") * n
  // whole: scala.util.PropertiesTrait.userHome: reader: the environment
  val home: Int -> String = n => scala.util.Properties.userHome * n
  // whole: java.lang.Integer.getInteger: reader: the environment and the system properties
  val prop: Int -> Integer = n => Integer.getInteger("x", n)
  // whole: scala.runtime.coverage.Invoker.invoked: it is not on the allowlist
  val write: Int -> Unit = n => scala.runtime.coverage.Invoker.invoked(n, "/tmp/okay-safe-coverage")
  def block(lock: Object): Unit = lock.wait() // whole: java.lang.Object.wait: async: threads, executors and monitors
  def monitor(lock: Object): Int = lock.synchronized(1) // whole: java.lang.Object.synchronized: a monitor
