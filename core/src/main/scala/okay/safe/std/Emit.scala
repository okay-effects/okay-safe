package okay.safe.std

import language.experimental.safe
import language.experimental.separationChecking

import okay.safe.*

/** EMIT: a stream of values, possibly endless. The producer is a program that sends; what a stream is made into —
 * a list, a loop, another stream, a lazy sequence pulled a value at a time — is the handler's choice. */
enum Emit[T, X]:
  case Send[T](value: T) extends Emit[T, Unit]

object Emit:
  def send[T](value: T): Word[Emit % T, Unit] = word(Send(value))

  /** every value of a sequence, which may be lazy and endless */
  def sendAll[T, R[_]](values: IterableOnce[T])(using In[Emit % T, R]): Unit ! R =
    def from(left: LazyList[T]): Unit ! R =
      if left.isEmpty then unit else send(left.head).flatMap(_ => from(left.tail))
    from(LazyList.from(values))

  /** the values sent, with the program's own */
  def collect[T, R[_], Rest[_], A](p: A ! R)(using Split[Emit % T, R, Rest]): (List[T], A) ! Rest =
    handle(p, Handler.stateful[Emit % T, Rest, A, (List[T], A), List[T]](Nil)((sent, a) => pure((sent.reverse, a)))(
      [X] => (op: Emit[T, X], sent: Cell[List[T]]^) => op match
        case Send(value) => sent.set(value :: sent.get)))

  /** `f` run for each value, as it is sent */
  def each[T, R[_], Rest[_], A](f: T -> Unit ! Rest)(p: A ! R)(using Split[Emit % T, R, Rest]): A ! Rest =
    handle(p, Handler.control[Emit % T, Rest, A, A](pure)(
      [X] => (op: Emit[T, X], k: Continuation[X, Rest, A]) => op match
        case Send(value) => f(value).flatMap(u => k(u))))

  /** a stream of the values, each through `f`: a new stream. Its row `S` is `R` with `Emit % U` for `Emit % T`:
   * the one expected, or with nothing said the new stream last. The program is put into the row of both streams,
   * and the old one is handled there. */
  def map[T, U, R[_], Rest[_], S[_], A](f: T -> U)(p: A ! R)(
      using from: Split[Emit % T, R, Rest], to: Split[Emit % U, S, Rest]): A ! S =
    each[T, S + Emit % T, S, A](value => send(f(value)))(p.into(using Sub.exchanged(from, to)))

  /** a stream of the values that `keep`: an interceptor — those kept are sent on, to the stream's handler */
  def filter[T, R[_], A](keep: T -> Boolean)(p: A ! R)(using In[Emit % T, R]): A ! R =
    intercept(p, Handler.control[Emit % T, R, A, A](pure)(
      [X] => (op: Emit[T, X], k: Continuation[X, R, A]) => op match
        case Send(value) => if keep(value) then send(value).flatMap(k) else k(())))

  /** a stream of the first `n` values; the producer is not resumed after them: it is left there, unwound. What
   * its finalizers send then goes to the stream's handler, past this cut. */
  def take[T, R[_], A](n: Int)(p: A ! R)(using In[Emit % T, R]): Unit ! R =
    type Left = Int -> Unit ! R
    intercept(p, Handler.control[Emit % T, R, A, Left](_ => pure(_ => unit))(
      [X] => (op: Emit[T, X], k: Continuation[X, R, Left]) => op match
        case Send(value) => pure: left =>
          if left <= 0 then k.unwind
          else send(value).flatMap(_ => if left == 1 then k.unwind else k(()).flatMap(_(left - 1)))))
      .flatMap(_(n))

