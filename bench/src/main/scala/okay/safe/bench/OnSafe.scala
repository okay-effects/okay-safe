package okay.safe.bench

import okay.safe.*
import okay.safe.std.*

/** the four programs on okay-safe. Host code: not capture checked, as the bench needs no checks of its own */
object OnSafe:
  /** n turns of get and put, one handler outside */
  def stateLoop(n: Int)(using Runner): Int =
    def loop[R[_]: Has[State % Int]](i: Int): Int ! R =
      if i == 0 then State.get[Int].into[R]
      else State.get[Int].flatMap(s => State.put(s + 1).flatMap(_ => loop[R](i - 1)))
    run(State.run(0)(loop[State % Int](n)))._2

  /** n asks, one handler outside */
  def outside(n: Int)(using Runner): Int =
    def loop[R[_]: Has[Reader % Int]](i: Int, sum: Int): Int ! R =
      if i == 0 then pure(sum) else Reader.ask[Int].flatMap(x => loop[R](i - 1, sum + x))
    run(Reader.run(1)(loop[Reader % Int](n, 0)))

  /** n asks, each step a handler around the rest: n handlers deep at the end */
  def around(n: Int)(using Runner): Int =
    def loop(i: Int): Int ! Pure =
      if i == 0 then pure(0)
      else Reader.run(1)(Reader.ask[Int].flatMap(x => loop(i - 1).into[Reader % Int].map(_ + x)))
    run(loop(n))

  /** n asks, each continuation running the rest as a run of its own: refused — NestedRun at the first step */
  def inside(n: Int)(using Runner): Int =
    def loop(i: Int): Int ! Pure =
      if i == 0 then pure(0)
      else Reader.run(1)(Reader.ask[Int].flatMap(x => pure(x + run(loop(i - 1)))))
    run(loop(n))
