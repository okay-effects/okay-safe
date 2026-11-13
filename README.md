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
sensitive effect is answered only under its audit; its continuations and clauses are pure, so resuming a continuation
twice is the same computation twice, never a replay. Not covered: non-termination and resource exhaustion, exceptions
the runtime throws by itself, a handler's cell given away read-only, and the trusted base — the kernel's machine, the
allowlisted standard library, the JVM — and the host, which runs programs and is trusted, not checked.

## Modules

| module | what |
|---|---|
| `core` | the kernel, `okay.safe`: rows, programs, handlers, the machine, the audit, the standard effects. Programs' API in safe mode; `Host.scala` (capture checked) and `Unchecked.scala` for the host only |
| `example` | an application: a sensitive `Bank` effect and a payroll in safe mode, the real client held by one handler at the host's edge, audited |
| `whole` | the whole-program check: reads every TASTy file of a program and its dependencies, holds every reference to a default-deny allowlist |
| `checks` | the guarantees tested: `cases/` compiled by the compiler itself (the exact error expected, or the whole-program finding), and run-time checks (NestedRun, JPMS, Unsafe) |
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

The host, the one place a program is run (capture checked, not safe mode):

```scala
import language.experimental.captureChecking
import okay.safe.*

@main def app(): Unit = Runner.host:
  println(run(State.run(0)(tick[State % Int])))
```

A handler that holds an authority (a client, a connection) is made in the host with `Authority`; a sensitive effect
(`given Sensitive[Bank]`) is handled only by `handleAudited` under `audit(interceptor)` — see
`example/src/main/scala/okay/safe/example`.

The whole-program check of an application, its host package apart:

```
sbt "whole/runMain okay.safe.whole.Main --program <classes and jars of the app and its dependencies> \
  --classpath <kernel and standard library> --host my.app.host [--explicit-nulls]"
```

Run with the kernel on the module path and `--sun-misc-unsafe-memory-access=deny` for the run-time checks.

## Build

`sbt test` — the kernel's suites (the okay-effects TCK digest, Properties, Audits) and the checks. `sbt bench/run` —
the benchmark. Scala 3 nightly `3.10.2-RC1-bin-20261113-2905165-NIGHTLY`, JDK 26.
