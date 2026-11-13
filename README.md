# okay-safe

A research kernel of algebraic effects — the [okay-effects](https://github.com/okay-effects) model: programs as data,
handlers, one CEK machine, multi-shot continuations as immutable values — made as safe as Scala 3 can make it, and
then checked where the compiler stops. The question it answers: **can effectful Scala code have full guarantees of
purity and safety?** The answer, with every mechanism tried and every gap found, is in
[docs/research.md](docs/research.md).

## The guarantee

A program compiled in safe mode with capture and separation checking, and passing the whole-program check, is a
value: built, it does nothing; run, it performs effects only by asking operations that handlers answer. It cannot run
a program, reach a client, the clock, the console, a thread or a file except through a handler the host made; a
sensitive effect is answered only under its audit; a handler's state never leaves its clause readable; its
continuations and clauses are pure, so resuming a continuation twice is the same computation twice, never a replay.
A deployment proves it was checked: the gate seals what it compiled and checked, and the kernel's `Launch` gives the
application's entry its one runner only when the seal, the JVM flag and the module path match. Not covered:
non-termination and resource exhaustion, exceptions the runtime throws by itself, the trusted base — the kernel's
machine and launcher, the allowlisted standard library, the JVM —, the host, which runs programs and is trusted, not
checked, and who wrote a seal: it is not signed.

## Modules

| module | what |
|---|---|
| `core` | the kernel, `okay.safe`: rows, programs, handlers, the machine, the audit, the standard effects. Programs' API in safe mode; `Host.scala` (capture checked) and `Unchecked.scala` for the host only; `Launch.scala`, the entry, which verifies the seal |
| `example` | an application, `okay.bank`: a sensitive `Bank` effect and a payroll in safe mode, the real client held by one handler at the host's edge, audited |
| `whole` | the whole-program check: reads every TASTy file of a program and its dependencies, holds every reference to a default-deny allowlist |
| `gate` | the gate: compiles with the required flags, runs the whole-program check, seals a deployment (`app.jar` with its seal, the kernel as a named module, `app.args`) |
| `agent` | a gate for agents' code: a program over a log, the clock and the bank compiled, checked, then run with the host's handlers — the bank behind an audit that asks a `Guard` effect, the policy a handler in okay-guard's shape |
| `checks` | the guarantees tested: `cases/` compiled by the compiler itself (the exact error expected, or the whole-program finding), and run-time checks (NestedRun, JPMS, Unsafe), the sealed deployment (SealTest) |
| `bench` | against okay-scala 0.1.1: a state loop and three ways of nesting handlers |

## Use

A program file:

```scala
import language.experimental.safe
import language.experimental.separationChecking
import okay.safe.*
import okay.safe.std.*

def tick[R[_]: Has[State % Int]]: Int ! R = State.get[Int].flatMap(n => State.put(n + 1).map(_ => n))
```

The host, the one place a program is run (capture checked, not safe mode): its entry is given the runner once, by
the kernel's `okay.safe.Launch` — a runner is never made anywhere else:

```scala
import language.experimental.captureChecking
import okay.safe.*

object App extends Entry:
  def run(args: List[String])(using Runner): Unit =
    println(okay.safe.run(State.run(0)(tick[State % Int])))
```

A handler that holds an authority (a client, a connection) is made in the host with `Authority`; a sensitive effect
(`given Sensitive[Bank]`) is handled only by `handleAudited` under `audit(interceptor)` — see
`example/src/main/scala/okay/bank`.

The whole-program check of an application, its host package apart:

```
sbt "whole/runMain okay.safe.whole.Main --program <classes and jars of the app and its dependencies> \
  --classpath <kernel and standard library> --host my.app.host [--explicit-nulls]"
```

A sealed deployment — compiled, checked and recorded by the gate, started only when it matches:

```
sbt "gate/runMain okay.safe.gate.Main --sources <dirs> --host my.app.host --entry my.app.host.App \
  --kernel <core classes> --library <scala-library.jar> --out deploy"
cd deploy && java @app.args
```

An agent's program through the gate (`agent/programs` has one of each kind):

```
sbt "agent/run agent/programs/good.scala"
```

## Build

`sbt test` — the kernel's suites (the okay-effects TCK digest, Properties, Audits), the checks, the seal, the agents'
gate. `sbt bench/run` —
the benchmark. Scala 3 nightly `3.10.2-RC1-bin-20261113-2905165-NIGHTLY`, JDK 26.
