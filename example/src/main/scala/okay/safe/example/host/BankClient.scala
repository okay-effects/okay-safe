package okay.safe.example.host

import language.experimental.captureChecking

/** THE REAL CLIENT: what stands for a bank's API — a connection, a key. A capability: code that holds one says so
 * in its type, and code compiled in safe mode cannot name this class at all. Here the accounts are kept in memory;
 * every call is counted, so a test sees whether anything reached the bank but through its handler. */
final class BankClient(opening: Map[String, Long]) extends caps.SharedCapability:
  private val accounts = java.util.concurrent.ConcurrentHashMap[String, java.lang.Long]()
  private val counted = java.util.concurrent.atomic.AtomicInteger()
  opening.foreach((name, amount) => accounts.put(name, amount))

  def balance(account: String): Long =
    counted.incrementAndGet()
    accounts.getOrDefault(account, 0L)

  def transfer(from: String, to: String, amount: Long): Unit =
    counted.incrementAndGet()
    accounts.merge(from, -amount, (a, b) => a + b): Unit
    accounts.merge(to, amount, (a, b) => a + b): Unit

  /** how many calls reached the bank */
  def calls: Int = counted.get
