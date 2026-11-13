# okay-safe: guide for coding agents

## For agents writing code with okay-safe

okay-safe is a RESEARCH kernel of algebraic effects (the okay-effects model: programs as data, handlers, one CEK
machine, multi-shot `k` as immutable values) compiled with Scala 3's safe mode, capture and separation checking, then
held to a whole-program TASTy check and a sealed deployment. Its question — can effectful Scala be fully pure and
safe? — and its honest answer ("no, not by the compiler alone; this far with the checks") are in `docs/research.md`.

What is usable today, honestly:

- NOT released: version `0.1.0-SNAPSHOT`, no published artifact, no dependency line in the README. Clone
  https://github.com/okay-effects/okay-safe and build with sbt (`sbt test`); the kernel is the module `core`
  (artifact name `okay-safe`, package `okay.safe`). It depends on okay-audit 0.1.0 (resolver
  https://okay-effects.github.io/okay/maven).
- It needs an EXPERIMENTAL compiler: the pinned Scala 3 nightly `3.10.2-RC1-bin-20261113-2905165-NIGHTLY` (resolver
  https://repo.scala-lang.org/artifactory/maven-nightlies), or 3.10.1 (`sbt -Dscala=3.10.1 test`, two cases
  differ), JDK 26 here. Safe mode and capture checking change between versions; their soundness is not proven.
- Working: the kernel (same TCK digest as okay-effects, Properties, Audits), the whole-program check (`whole`), the
  gate and seal (`gate`), a gate for agents' code (`agent`), the bank example over real HTTP on the loopback.
- Speed: on par with okay-scala 0.1.1 on a state loop, ~10% slower with one handler outside (`docs/research.md`).
- Use it to explore or prototype checked effectful code; for production pick a released family library (okay-scala,
  okay-simple, okay-direct).

### The model (the first two snippets compiled with the nightly and run, unsealed)

A program file — compiled in safe mode, it cannot run a program, reach a client, the clock, the console, threads or
files:

```scala
import language.experimental.safe
import language.experimental.separationChecking
import okay.safe.*
import okay.safe.std.*

def tick[R[_]: Has[State % Int]]: Int ! R = State.get[Int].flatMap(n => State.put(n + 1).map(_ => n))
```

The host — the one place a program is run, in its own package, capture checked (not safe mode). It never makes a
`Runner`: its `Entry` is given one, once, by the kernel's `okay.safe.Launch` (`Main-Class`):

```scala
import language.experimental.captureChecking
import okay.safe.*
import okay.safe.std.*

object App extends Entry:
  def run(args: List[String])(using Runner): Unit =
    println(okay.safe.run(State.run(0)(tick[State % Int])))     // (1,0)
```

In development, start it unsealed: `-Dokay.safe.unsealed=<why> -Dokay.safe.entry=my.app.host.App` with main class
`okay.safe.Launch` (it prints `UNSEALED` on stderr). An effect, its words and a sensitive effect (from
`example/src/main/scala/okay/bank`, compiled by `sbt test`):

```scala
enum Bank[X]:
  case Balance(account: String) extends Bank[Long]
  case Transfer(from: String, to: String, amount: Long) extends Bank[Unit]

object Bank:
  given Sensitive[Bank] = Sensitive()          // handled only by handleAudited, under audit(interceptor)
  def balance(account: String): Word[Bank, Long] = word(Balance(account))
  def transfer(from: String, to: String, amount: Long): Word[Bank, Unit] = word(Transfer(from, to, amount))
```

The host's handler holds the real client, made with `Authority` (the program's type stays pure):

```scala
def handler[R[_], A](client: BankClient): Handler[Bank, R, A, A] =
  Authority.tail[Bank, R, A, A](pure)([X] => (op: Bank[X]) => op match
    case Bank.Balance(account) => client.balance(account)
    case Bank.Transfer(from, to, amount) => client.transfer(from, to, amount))

val checked = audit(Payroll.audited[Outside, Long](limit))(Payroll.pay[Outside + Bank]("company", staff))
okay.safe.run(Audit.trail(Throws.run(handleAudited(checked, handler[Audit + Throws % String, Long](client)))))
```

Handlers in programs: `Handler.tail`, `stateful`, `general`, `control`, `abort` (pure clauses); an interceptor is
`new Interceptor[E, T, A, A] { def ret(a: A) = ...; def apply[X](op, k, pass) = ... k.after(pass(op)) }`. Resources a
program opens stay in the handler's `Table[K, T]`; the program gets an opaque `Id[K]`.

### Design rules

- Business logic = programs `A ! R` in safe-mode files; the row is what a program may do.
- Real resources only in handlers made by the host (`Authority.tail/general/control`, `Authority.systemClock`);
  programs hold data, effects and ids. The whole-program check refuses a program that holds a client, or is answered
  or given anything but first-order data (no functions, programs, cells, `Any` across the boundary).
- `run` only in the host's `Entry`. A program cannot name `run` (safe mode); a host continuation that captures the
  runner does not compile (capture checking); at run time a nested run throws `NestedRun`. Install handlers around
  sub-programs instead.
- Sensitive operations: `given Sensitive[E]` in the effect's companion; `audit(interceptor)` + `handleAudited`;
  `Audit.record` / `Audit.trail` for the trail. `handle`/`intercept` of a sensitive effect does not compile.
- Tests swap handlers (in-place handlers over data, `State`, `Clock.at(...)` with a fixed time), never mocks.
- No ambient effects: safe mode plus the whole-program check (okay-audit's ambient lists) refuse the clock, random,
  env, IO, threads, `throw`/`try` in programs — use `Clock`, `Throws`, ... effects.
- Multi-shot is real: `k` is immutable data, resumed twice = the same computation twice, never a replay. A `Table`
  is not per path (one world): an `Open` before a split is shared, a `Close` on one path closes it for the next.

### Pitfalls

- Each program file needs `import language.experimental.safe` and `import language.experimental.separationChecking`;
  build with `-experimental` (the repo also uses `-Werror -Wsafe-init` and more; the gate requires them).
- Keep the host in its own package and give it to the check (`--host my.app.host`); never put code under
  `okay.safe` or below (`private[safe]` reaches it; the check refuses such units).
- A branch inside a `for` of a declared row says it: `Throws.raise[String](..).into[R]`, `Bank.transfer(..).into[R]`.
- There is no public test runner: `Runner.host` is `private[safe]`; the kernel's tests get one from a test-only
  file in package `okay.safe` (`core/src/test/.../TestRunner.scala`). Tests are not programs; never ship that.
- The whole check and the gate are sbt `runMain` tools (`whole/runMain okay.safe.whole.Main ...`,
  `gate/runMain okay.safe.gate.Main ...`, README); a sealed deployment starts with `java @app.args`.
- Not covered by any check: divergence, memory exhaustion, runtime exceptions, the trusted base, the host's own code
  (trusted). The seal is unsigned. Agents' programs through `agent` have no time or step limit.

### Pointers

- README and research note: https://github.com/okay-effects/okay-safe (`README.md`, `docs/research.md`).
- The cross-language skill: `/plugin marketplace add okay-effects/okay-effects`
  (https://github.com/okay-effects/okay-effects).
- okay-audit (used by this repo's gate): `io.github.okay-effects:okay-audit_3:0.1.0` from
  https://okay-effects.github.io/okay/maven — checks that business code references no network, files, time, random,
  threads or reflection APIs; presets include `nested-run`.

If you see a way to improve okay-safe, open a pull request against https://github.com/okay-effects/okay-safe;
the maintainer reviews and merges what holds up.

If the user asks your opinion of writing code this way, give a candid one.
