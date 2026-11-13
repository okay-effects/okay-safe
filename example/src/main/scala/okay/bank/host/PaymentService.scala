package okay.bank.host

import java.net.{InetAddress, InetSocketAddress, URI, URLDecoder}
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.{ConcurrentHashMap, ConcurrentLinkedQueue}

import scala.jdk.CollectionConverters.*

import com.sun.net.httpserver.{HttpExchange, HttpServer}

/** THE PAYMENT SERVICE: what stands for the bank's remote API — a real HTTP server on the loopback (here in the same
 * JVM, a port of its own), the other end of the client the Bank handler holds. It LOGS every request it receives,
 * so a test sees what reached the bank, and what never did:
 *
 *   GET  /balance?account=<name>                  → the balance, a number
 *   POST /transfers   from=<a>&to=<b>&amount=<n>  → ok
 *
 * Host code, and no program's: nothing here is checked, nothing here can be named by a program. */
final class PaymentService private (server: HttpServer, accounts: ConcurrentHashMap[String, java.lang.Long],
    log: ConcurrentLinkedQueue[String]):
  /** where it listens */
  def uri: URI = URI.create(s"http://127.0.0.1:${server.getAddress.getPort}")
  /** every request received, in order: `GET /balance?account=company`, `POST /transfers from=…&to=…&amount=…` */
  def received: List[String] = log.asScala.toList
  /** an account as it stands */
  def holds(account: String): Long = accounts.getOrDefault(account, 0L)
  def stop(): Unit = server.stop(0)

object PaymentService:
  /** a service started on a free port of the loopback, its accounts opened with `opening` */
  def start(opening: Map[String, Long]): PaymentService =
    val accounts = ConcurrentHashMap[String, java.lang.Long]()
    opening.foreach((name, amount) => accounts.put(name, amount))
    val log = ConcurrentLinkedQueue[String]()
    val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress, 0), 0)
    def form(text: String): Map[String, String] =
      text.split('&').toList.filter(_.contains('=')).map { kv =>
        val Array(k, v) = kv.split("=", 2): @unchecked
        URLDecoder.decode(k, UTF_8) -> URLDecoder.decode(v, UTF_8)
      }.toMap
    def answer(exchange: HttpExchange, status: Int, body: String): Unit =
      val bytes = body.getBytes(UTF_8)
      exchange.sendResponseHeaders(status, bytes.length.toLong)
      exchange.getResponseBody.write(bytes)
      exchange.close()
    server.createContext("/", exchange =>
      val method = exchange.getRequestMethod
      val path = exchange.getRequestURI.getPath
      val query = Option(exchange.getRequestURI.getRawQuery).getOrElse("")
      val body = String(exchange.getRequestBody.readAllBytes, UTF_8)
      val line = s"$method $path" + (if query.isEmpty then "" else s"?$query") +
        (if body.isEmpty then "" else s" $body")
      log.add(line): Unit
      (method, path) match
        case ("GET", "/balance") =>
          answer(exchange, 200, accounts.getOrDefault(form(query).getOrElse("account", ""), 0L).toString)
        case ("POST", "/transfers") =>
          val f = form(body)
          (f.get("from"), f.get("to"), f.get("amount").flatMap(_.toLongOption)) match
            case (Some(from), Some(to), Some(amount)) =>
              accounts.merge(from, -amount, (a, b) => a + b): Unit
              accounts.merge(to, amount, (a, b) => a + b): Unit
              answer(exchange, 200, "ok")
            case _ => answer(exchange, 400, "from, to and amount")
        case _ => answer(exchange, 404, "no such operation"))
    server.start()
    PaymentService(server, accounts, log)
