package okay.bank.host

import language.experimental.captureChecking

import java.net.{URI, URLEncoder}
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.charset.StandardCharsets.UTF_8

/** THE REAL CLIENT: the bank's API over HTTP — a `java.net.http.HttpClient`, made by the host, and where the service
 * is (PaymentService.scala). A capability: code that holds one says so in its type, and code compiled in safe mode
 * cannot name its class's statics, nor make one (`new` is checked). ITS API IS THE HOST'S: every member is
 * `private[host]`, so a program given a client — a parameter, which safe mode does not follow into its instance
 * members — cannot call it: "cannot be accessed as a member of BankClient". Only the host's package reaches it, and
 * there only the handler (BankHost.handler) calls it. The `HttpClient` inside is a Java object: capture checking
 * does not track it, and safe mode lets a program call its instance members — so the host never gives it to a
 * program, and the whole-program check refuses a program that holds one (the boundary rule, docs/research.md). */
final class BankClient(http: HttpClient, service: URI) extends caps.SharedCapability:
  private def body(request: HttpRequest): String =
    val response = http.send(request, HttpResponse.BodyHandlers.ofString())
    if response.statusCode != 200 then sys.error(s"the bank answered ${response.statusCode}: ${response.body}")
    response.body

  private def encoded(s: String): String = URLEncoder.encode(s, UTF_8)

  private[host] def balance(account: String): Long =
    body(HttpRequest.newBuilder(service.resolve(s"/balance?account=${encoded(account)}")).GET().build()).toLong

  private[host] def transfer(from: String, to: String, amount: Long): Unit =
    val form = s"from=${encoded(from)}&to=${encoded(to)}&amount=$amount"
    body(HttpRequest.newBuilder(service.resolve("/transfers"))
      .header("Content-Type", "application/x-www-form-urlencoded")
      .POST(HttpRequest.BodyPublishers.ofString(form)).build()): Unit
