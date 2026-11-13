package okay.safe.bench

import okay.safe.{Entry, Runner}

/** THE BENCHMARK: `bench/run` — each program on each library, warmed, then timed; the minimum and the median of the
 * timed runs, in milliseconds. `inside` is run once in a JVM of its own with -Xss512k, at growing depths. Started by
 * the kernel's `Launch`, unsealed (build.sbt): the runner is given only there. */
object Bench extends Entry:
  def time(body: => Int): Double =
    val start = System.nanoTime()
    val result = body
    if result == Int.MinValue then println("never")
    (System.nanoTime() - start) / 1e6

  def measure(name: String, body: => Int, warm: Int = 10, timed: Int = 15): Unit =
    for _ <- 1 to warm do time(body): Unit
    val runs = (1 to timed).map(_ => time(body)).sorted
    println(f"$name%-34s min ${runs.head}%9.2f ms   median ${runs(runs.size / 2)}%9.2f ms")

  def run(args: List[String])(using Runner): Unit =
    if args.headOption.contains("inside") then insideOnly(args(1), args(2).toInt)
    else
      val n = 1_000_000
      val deep = 1_000_000
      println(s"java ${sys.props("java.version")}, -Xss ${sys.props.getOrElse("bench.xss", "default")}")
      measure(s"okay-scala  state loop n=$n", OnOkayScala.stateLoop(n))
      measure(s"okay-safe   state loop n=$n", OnSafe.stateLoop(n))
      measure(s"okay-scala  outside n=$n", OnOkayScala.outside(n))
      measure(s"okay-safe   outside n=$n", OnSafe.outside(n))
      measure(s"okay-scala  around n=$deep", OnOkayScala.around(deep))
      measure(s"okay-safe   around n=$deep", OnSafe.around(deep))

  /** `inside` at depth n: its value, or the throwable that stopped it */
  def insideOnly(library: String, n: Int)(using Runner): Unit =
    val result =
      try (if library == "okay-scala" then OnOkayScala.inside(n) else OnSafe.inside(n)).toString
      catch
        case _: StackOverflowError => "StackOverflowError"
        case e: RuntimeException => e.getClass.getSimpleName
    println(s"$library inside n=$n: $result")
