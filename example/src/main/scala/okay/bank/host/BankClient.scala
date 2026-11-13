package okay.bank.host

import language.experimental.captureChecking

/** THE REAL CLIENT: what stands for a bank's API — a connection, a key. A capability: code that holds one says so
 * in its type, and code compiled in safe mode cannot name its class's statics, nor make one (`new` is checked).
 * ITS API IS THE HOST'S: every member is `private[host]`, so a program given a client — a parameter, which safe
 * mode does not follow into its instance members — cannot call it: "cannot be accessed as a member of BankClient".
 * Only the host's package reaches it, and there only the handler (BankHost.handler) calls it. Here the accounts are
 * kept in memory; every call is counted, so a test sees whether anything reached the bank but through its handler. */
final class BankClient(opening: Map[String, Long]) extends caps.SharedCapability:
  private val accounts = java.util.concurrent.ConcurrentHashMap[String, java.lang.Long]()
  private val counted = java.util.concurrent.atomic.AtomicInteger()
  opening.foreach((name, amount) => accounts.put(name, amount))

  private[host] def balance(account: String): Long =
    counted.incrementAndGet()
    accounts.getOrDefault(account, 0L)

  private[host] def transfer(from: String, to: String, amount: Long): Unit =
    counted.incrementAndGet()
    accounts.merge(from, -amount, (a, b) => a + b): Unit
    accounts.merge(to, amount, (a, b) => a + b): Unit

  /** how many calls reached the bank */
  private[host] def calls: Int = counted.get

  /** an account as it stands, not counted: for the host's report */
  private[host] def holds(account: String): Long = accounts.getOrDefault(account, 0L)
