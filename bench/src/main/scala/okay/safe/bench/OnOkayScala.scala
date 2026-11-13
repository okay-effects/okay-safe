package okay.safe.bench

import okay.scala3.*
import okay.scala3.std.*

/** the same four programs on okay-scala 0.1.1 */
object OnOkayScala:
  def stateLoop(n: Int): Int =
    def loop[R[_]: Has[State % Int]](i: Int): Int ! R =
      if i == 0 then State.get[Int, R]
      else State.get[Int, R].flatMap(s => State.put[Int, R](s + 1).flatMap(_ => loop[R](i - 1)))
    run(State.run(0)(loop[State % Int](n)))._2

  def outside(n: Int): Int =
    def loop[R[_]: Has[Reader % Int]](i: Int, sum: Int): Int ! R =
      if i == 0 then pure(sum) else Reader.ask[Int, R].flatMap(x => loop[R](i - 1, sum + x))
    run(Reader.run(1)(loop[Reader % Int](n, 0)))

  def around(n: Int): Int =
    def loop(i: Int): Int ! Pure =
      if i == 0 then pure(0)
      else Reader.run(1)(Reader.ask[Int, Reader % Int].flatMap(x => loop(i - 1).into[Reader % Int].map(_ + x)))
    run(loop(n))

  def inside(n: Int): Int =
    def loop(i: Int): Int ! Pure =
      if i == 0 then pure(0)
      else Reader.run(1)(Reader.ask[Int, Reader % Int].flatMap(x => pure(x + run(loop(i - 1)))))
    run(loop(n))
