package okay.safe

/*
 * WHAT THE HOST DOES THAT CAPTURE CHECKING CANNOT SAY. This file is compiled with no capture checking and not in safe
 * mode: so nothing here is checked, and nothing here can be named by code compiled in safe mode. It is part of the
 * trusted base, beside the machine (docs/research.md, "the trusted base"):
 *
 *   - a handler whose clauses hold an authority of the host — a bank's client, a connection, the clock. The kernel's
 *     handlers take pure functions; under capture checking a polymorphic clause that captures a capability cannot
 *     be made pure (`unsafeAssumePure` strips the outer capture set of `[X] -> (...) ->{any} B`, not the inner one),
 *     so these are made here, where nothing is checked. The capabilities they hold are hidden from the program's
 *     type, which stays pure: the program asks the effect; only this handler, made at the edge, calls the client.
 *   - a stream pulled lazily: `LazyList` is a pure type of the capture-checked standard library, and its tail would
 *     capture the runner.
 */
object Authority:
  def general[E[_], R[_], A, B](ret: A => B ! R)(on: [X] => (E[X], X => B ! R) => B ! R): Handler[E, R, A, B] =
    Handler.general(ret)(on)

  def control[E[_], R[_], A, B](ret: A => B ! R)(on: [X] => (E[X], Continuation[X, R, B]) => B ! R)
      : Handler[E, R, A, B] = Handler.control(ret)(on)

  def tail[E[_], R[_], A, B](ret: A => B ! R)(answer: [X] => E[X] => X): Handler[E, R, A, B] =
    Handler.tail(ret)(answer)

  /** the system's clock, for `Clock`: the host's, since reading it is an effect */
  def systemClock[R[_], Rest[_], A](p: A ! R)(using Split[std.Clock, R, Rest]): A ! Rest =
    std.Clock.at(() => System.currentTimeMillis())(p)

  /** THE VALUES OF A STREAM, pulled as they are needed: each pull runs the producer to its next value. A run of
   * its own: forced inside a running program — a continuation that reads it — it is a nested run, and refused
   * (`NestedRun`); the host reads it. */
  def pull[T, A](p: A ! std.Emit % T)(using Runner): LazyList[T] =
    run(handle(p, Handler.control[std.Emit % T, Pure, A, LazyList[T]](_ => pure(LazyList.empty))(
      [X] => (op: std.Emit[T, X], k: Continuation[X, Pure, LazyList[T]]) => op match
        case std.Emit.Send(value) => pure(value #:: run(k(()))))))

/** A HANDLER'S TABLE OF RESOURCES: what a program opens, kept here — the host's — and given to the program as an
 * `Id` (Id.scala). A handler made by `Authority` holds the table: `Open` adds the real resource and answers its id;
 * `Read(id)`, `Close(id)` find it, or find none — a closed id, another table's — and the handler refuses the
 * operation (a failure of the program, an effect: never the resource, never a forged one).
 *
 * IT IS NOT THE PATH'S: a handler's `Cell` belongs to the path, as the program's state does, but a table holds the
 * world — a connection is not copied when a continuation is resumed twice. So under a handler that resumes many
 * times (`Choose`) every path sees one table: what one path opened the next may use, and what one path closed is
 * closed for the next. Nothing is replayed: `Open` ran once, where it was asked, before the paths split. The host
 * closes what is left when the run is over: `drain`. */
final class Table[K <: AnyKind, T]:
  private val held = java.util.concurrent.ConcurrentHashMap[Long, T]()

  /** `resource` kept: its id, new */
  def add(resource: T): Id[K] =
    val id = Table.next.incrementAndGet()
    held.put(id, resource): Unit
    Id.make(id)

  /** the resource of `id`; none when it was closed, or is not of this table */
  def get(id: Id[K]): Option[T] = Option(held.get(id.number))

  /** the resource of `id`, taken out: none when it was closed already, or is not of this table */
  def remove(id: Id[K]): Option[T] = Option(held.remove(id.number))

  /** how many are open */
  def size: Int = held.size

  /** every resource still open, taken out: for the host to close when the run is over */
  def drain(): List[T] =
    val all = scala.jdk.CollectionConverters.MapHasAsScala(held).asScala.keys.toList.sorted
    all.flatMap(n => Option(held.remove(n)))

object Table:
  /** ids are numbered across every table of the JVM: an id is in one table at most */
  private val next = java.util.concurrent.atomic.AtomicLong()
