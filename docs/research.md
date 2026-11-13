# Can effectful Scala have full guarantees of purity and safety?

A research note on okay-safe: the okay-effects model (programs as data, handlers, one CEK machine, multi-shot
continuations as immutable values) compiled with everything Scala 3 offers to check it, and checked again where the
compiler stops. Every claim below is a test: `checks/cases` (compiled by the compiler itself, every phase) and
`checks/src/test` (run time). `sbt test` runs them all.

## Short answer

**No — not by the compiler alone, and not "full".** With the nightly Scala 3 (capture checking, separation checking,
safe mode) a program compiled in safe mode cannot run a program, cannot capture the runner, a client, a cell or a
variable in a continuation, cannot keep global mutable Scala state, cannot cast, cannot use capture checking's escape
hatches, and cannot name the console, `System`, threads, futures, files or the network. That is a large part of the
way. But safe mode *assumes* whole packages safe — `scala.util`, `scala.runtime`, `java.time`, `java.lang.Math`,
`java.lang.Object` — and through them a program still reads the clock, random numbers, the environment, writes a
file (`scala.runtime.coverage.Invoker`) and rewrites immutable data through `sun.misc.Unsafe`
(`scala.runtime.LazyVals`). It checks static references only, so a constructor with type arguments
(`java.util.HashMap[K, V]()`), a type pattern (`case _: StackOverflowError`) and `@unchecked` on a scrutinee pass
unseen; it trusts `@assumeSafe` from any library compiled anywhere, and package-private access is open to any file
that says `package okay.safe`. Exceptions are allowed.

Adding a **whole-program check of the TASTy** (module `whole`: default-deny allowlist, transitive over the program
and every dependency given with it, no `throw`/`try`, no `@assumeSafe` outside safe mode, no class in the kernel's
packages, capture checking and explicit nulls read from each unit's TASTy attributes) closes every hole found here
that is visible in the code. Adding **JPMS** (the kernel a named module) and the JVM flag
`--sun-misc-unsafe-memory-access=deny` closes the split package and `Unsafe` at run time as well.

What remains — what no check here blocks — is listed under "Where full guarantees stop": divergence and resource
exhaustion, exceptions the runtime throws by itself, a cell given away read-only, the trusted base (the kernel's
machine, the standard library as allowlisted, the JVM), and the host: the edge where programs are run and
authorities given is ordinary code and is trusted, not checked.

## The guarantee, as it stands

> A program — every unit outside the host's packages, its dependencies included — compiled in safe mode with
> capture and separation checking, and passing the whole-program check with the default policy, is a value: it
> performs no effect when built; run, it performs effects only by asking operations, which only handlers answer;
> a handler holding an authority (a client, the clock) is made only by the host; a sensitive effect is answered only
> under its audit; continuations and clauses are pure functions, so a continuation resumed twice is the same
> computation twice — never a replay, since `k` is immutable data. Excluded: non-termination, stack or heap
> exhaustion, exceptions thrown by the runtime itself (arithmetic, a partial function of the standard library), and
> whatever the trusted base does.

## Versions and flags

| | |
|---|---|
| Scala | 3.10.2-RC1-bin-20261113-2905165-NIGHTLY (pinned in `build.sbt`) |
| JVM | OpenJDK 26.0.2.1 (Temurin), Apple M4 Max |
| sbt | 1.13.0; munit 1.3.6 |
| kernel and application | `-experimental -deprecation -feature -unchecked -Werror -Wunused:all -Wvalue-discard -Wnonunit-statement -Wimplausible-patterns -Wsafe-init` |
| per file | `import language.experimental.safe` (implies capture checking), `import language.experimental.separationChecking` |
| asked by cases | `-Yexplicit-nulls`, `-language:experimental.saferExceptions`, `-language:strictEquality`, `-Wsafe-init -Werror` |
| run time | `--sun-misc-unsafe-memory-access=deny`; the kernel as a named module (`okay.safe`, nothing opened) |

Scala 3.10.1 (`sbt -Dscala=3.10.1 test`): the kernel and its 87 tests pass; two cases differ — a polymorphic clause
that assigns `k` or its cell to a variable it closes over **compiles** under 3.10.1 and is refused by the nightly.
The guarantee above is the nightly's.

## The kernel's layers

| file | compiled | who may name it |
|---|---|---|
| `Effects.scala`, `Audit.scala`, `std/*` | safe mode + separation checking | everyone |
| `Rows.scala`, `Machine.scala`, `Messages.scala`, `Branches.scala` | plain, `@assumeSafe` (trusted base) | everyone; the machine is `private[safe]` |
| `Host.scala` — `Runner`, `run`, `step`, `NestedRun` | capture checked, not safe mode, not `@assumeSafe` | the host only |
| `Unchecked.scala` — `Authority` (handlers holding a capability, the system clock, `pull`) | no checks | the host only |

The split of the host into a capture-checked part (`Host.scala`) and an unchecked part (`Unchecked.scala`) is what
lets the host's own code be capture checked — a host continuation that captures the runner or the client does not
compile — while the handlers that must hold a capability inside a polymorphic pure clause (which capture checking
cannot express: `unsafeAssumePure` strips the outer capture set, not the inner one) are made where nothing is
checked, and their capabilities are hidden from the program's type.

## The matrix

Mechanisms: **CC** capture/separation checking; **SM** safe mode; **WP** the whole-program TASTy check; **RT** run
time (the machine's own check, JPMS, a JVM flag); **—** not blocked. Each row names its case(s) in `checks/cases`
or its test in `checks/src/test` (RuntimeTest, WholeTest).

| # | violation | compile time | whole-program | run time | case / test |
|---|---|---|---|---|---|
| 1 | run inside a continuation, program in safe mode | SM: `run` not nameable; typer: no `Runner` | WP: `okay.safe.run` denied | `NestedRun` | safe-run-in-continuation, safe-run-without-runner |
| 1 | host (CC) continuation captures its entry's runner | CC: `capability r cannot flow into capture set {}` | host exempt | `NestedRun` | host-continuation-captures-runner |
| 1 | host continuation makes its own runner | access: `Runner`'s constructor private, `Runner.host` and `Launch` the kernel's — a runner is given once, to the `Entry` | host exempt | `NestedRun` | host-runner-made-in-continuation |
| 1 | host forges a runner with `null` | `-Yexplicit-nulls`: `Found: Null` | — | `NestedRun` | null-runner-host, null-runner-without-explicit-nulls |
| 2 | `k` into a variable the clause closes over | CC (nightly only) | — | — | k-into-captured-var |
| 2 | `k` into a global variable | CC: a class with a `var` must be `Stateful`/`Mutable` | — | — | k-into-global-var |
| 2 | `k` to another thread / a future | SM: `Thread`, `Future`, `ExecutionContext` | WP: not allowlisted | — | k-to-another-thread |
| 2 | `k` returned after its handler returned | allowed by design: `k` is immutable data | — | resumed thrice, prefix ran once | k-returned-is-a-value; RuntimeTest 2 |
| 3 | cell kept in a captured variable | CC (nightly only) | — | — | cell-into-var |
| 3 | cell made, or its state written, outside the kernel | access: `private[safe]` | — | — | cell-made-outside |
| 3 | cell read or written through a read-only reference | CC: `Cannot call update method get/set` — both are update methods | — | — | cell-escaped-is-read-only |
| 3 | cell given away as an answer, read later | CC: it escapes read-only, and a read-only cell is inert: `Cannot call update method get` | — | — | cell-as-answer |
| 3 | cell given away typed exclusive (`Leak[Cell[Int]^]`), written later | CC: `any … is not included in the allowed capture set` | — | — | cell-as-exclusive-answer |
| 3 | cell's state through `package okay.safe` | — | WP: kernel's package | JPMS: split package refused | cell-through-the-kernels-package; RuntimeTest JPMS |
| 3 | cell's state by `LazyVals.objCAS` | CC: a cell is no pure `Object` | WP | `--sun-misc-unsafe-memory-access=deny` | cell-by-lazyvals |
| 3 | cell's state by reflection | SM: `getDeclaredField` rejected | WP | JPMS: `InaccessibleObjectException` | reflection; RuntimeTest JPMS |
| 4 | program given the client calls it eagerly | access: the client's API is `private[host]` — `cannot be accessed as a member` | WP: "a program names the host" | — | program-names-the-client |
| 4 | program calls the client in a continuation | access, and CC: `capability client cannot flow…` | WP | — | program-continuation-calls-the-client |
| 4 | host, in its own package, calls the client in a continuation | CC | host exempt | — | host-continuation-calls-the-client |
| 4 | program handles the sensitive effect itself | macro: `the effect Bank is sensitive` | — | — | program-handles-the-bank |
| 4 | handler put where the audit should be | rows: `is not in the row` | — | — | handled-without-its-audit |
| 4 | program makes the authority-holding handler | SM: `Authority` | WP | — | handler-made-in-the-program |
| 4 | `Plain[Bank]` forged in `package okay.safe` | — | WP: kernel's package | JPMS | plain-forged-in-the-kernels-package |
| 4 | positive: audited application, client reached only by its handler | compiles | clean (host apart) | refused transfer never reaches the client | the-application; WholeTest; RuntimeTest 4 |
| 5 | console, `System.*` time/env/props/exit, `sys.*`, `UUID.randomUUID`, `Thread`, `java.io`, `java.nio.file`, `scala.io`, `java.net`, `java.util.Random`, `Runtime` | SM | WP | — | refused-by-safe-mode |
| 5 | `scala.util.Random`, `Math.random`, `java.time.*.now`, `Clock.systemUTC`, `scala.util.Properties` (env, home), `Integer.getInteger`, `coverage.Invoker` (writes a file), `Object.wait`, `synchronized` | **— (assumed safe), even in pure continuations** | WP | — | assumed-safe-by-safe-mode |
| 6 | global `var` | CC | — | — | global-var |
| 6 | global `Mutable` object used undeclared | CC: uses clause required | — | — | global-mutable-object |
| 6 | global array written by a pure function | CC | — | — | global-array |
| 6 | `scala.collection.mutable`, `scala.runtime.*Ref` | SM | WP | — | mutable-collections |
| 6 | Java collection by a constructor with type arguments, written from a pure function | **— (SM misses it, CC does not track Java objects)** | WP | — | java-collection-by-generic-constructor |
| 7 | `asInstanceOf` | SM | WP | — | cast |
| 7 | `caps.unsafe.*` (`unsafeAssumePure`, `untrackedCaptures`) | SM: `@rejectSafe` | WP | — | escape-hatches |
| 7 | `@unchecked` on a scrutinee | **— (SM misses it)** | WP | — | unchecked-annotation |
| 7 | `@assumeSafe` in safe mode | SM | — | — | assume-safe-in-safe-mode |
| 7 | reflection, `Class.forName`, `sun.misc.Unsafe` | SM | WP | JPMS | reflection |
| 7 | program without explicit nulls | — | WP: attribute | — | program-without-explicit-nulls |
| 8 | `throw` an unchecked exception, `try`/`catch`, `???` | **— (Throwable assumed safe)** | WP | escapes the run to the host | runtime-exception |
| 8 | checked exception thrown in a continuation | `saferExceptions` + CC: `CanThrow` captured | WP | — | checked-exception-safer |
| 8 | catch a `java.lang.Error` by a type pattern | **— (a type, not checked)** | WP: `try` | — | errors-are-not-safe |
| 8 | catch the machine's control throwables | none exist: the machine is a loop; `NestedRun`/`NoHandler` are thrown out of `run` to the host | — | — | — |
| 8 | `break` out of a continuation | CC: the label is a capability | — | — | boundary-break-in-continuation |
| 9 | Java class, not assumed safe | SM (`java.io.File`) — **but not `java.util.ArrayList[Int](4)`** | WP | — | java-class |
| 9 | `java.util.stream` (assumed safe) | — | WP | — | java-class |
| 9 | dependency compiled outside safe mode, `@assumeSafe` | **— (believed)** | WP: claim, unit, and what it does | — | dependency-assumed-safe |
| 10 | immutable `List` rewritten by `LazyVals` | **— (`scala.runtime` assumed safe; a list is pure)** | WP | `--sun-misc-unsafe-memory-access=deny` | immutable-by-lazyvals; RuntimeTest |
| 10 | field read before initialization | `-Wsafe-init -Werror` | — | — | safe-init |
| 10 | an operation compared with an unrelated value | `strictEquality` | — | — | strict-equality |

## Why each mechanism stops where it does

- **Capture checking** tracks capabilities: what a closure holds. A continuation is `X -> B ! R`, a clause
  `[X] -> (E[X], Continuation) -> ...`, so neither holds a runner, a client, a label, a variable or a writable cell.
  It does not track what is not a capability: a Java object, a static method, an exception, a `null` (which is
  pure). Polymorphic functions were a gap until recently (3.10.1 misses the variable case).
- **Separation checking** makes a cell `Mutable`: writing asks the exclusive reference; a reference that escapes
  through a type argument is read-only. It does not stop the read-only escape.
- **Safe mode** is an allowlist of *static* references, with whole packages assumed safe and anything carrying
  `@assumeSafe` believed. Instance members are taken as checked (they came from a value the program had), types are
  not checked except in `new`, imports are skipped, constructor proxies with type arguments and annotations on a
  scrutinee pass unseen. Its assumed packages are broad for convenience, not for safety: `scala.util.Random`,
  `scala.util.Properties`, `java.time.Instant.now`, `java.lang.Math.random`, `scala.runtime.LazyVals`,
  `scala.runtime.coverage.Invoker`.
- **The whole-program check** reads the TASTy of every program unit and dependency handed to it: a default-deny
  allowlist (`whole/.../Policy.scala`), its own reading of every term reference, `new`, annotation, `throw` and `try`
  — it trusts nothing the compiler assumed. Its limits: it needs TASTy (a Java or Scala 2 dependency is only ever
  *named*, and every name of it must be allowlisted); it trusts the standard library's and the JDK's allowlisted
  members to do what they say; it requires the `import language.experimental.safe` in the file (a
  `-language:experimental.safe` flag leaves no trace in TASTy); it does not see code the compiler generates after
  pickling (pattern matching's `MatchError`).
- **Run time** is the last resort: the machine refuses a nested run (`NestedRun`, a thread-local flag); JPMS refuses
  a split package and reflective access to the kernel; the JVM refuses `Unsafe` memory access when told to.

## Where full guarantees stop

1. **Totality.** Divergence (`def loop: Int ! R = loop`), stack and heap exhaustion, arithmetic exceptions, a
   standard-library partial function (`List.head` on `Nil`), `MatchError`: not effects the types say. The kernel runs
   in constant stack for binds, but a program may still build an unbounded value.
2. **A cell escapes read-only** (`cell-as-answer`). The program can read, after the fact, the state its path left.
3. **The trusted base**: the machine and rows (`@assumeSafe`, plain Scala), the standard library behind the
   allowlist, the JVM. The audit is as good as the kernel's code that enforces it.
4. **The host.** Running programs and giving authorities is the host's; host code can be capture checked (and here
   is) but is not held to the rules of programs. A host can always make a runner inside a continuation: only
   `NestedRun` stops that, at run time.
5. **Configuration is part of the guarantee.** The import in each file, the flags, the policy, the JVM flag and the
   module path must be what this note says; nothing in the code proves that a deployment used them.
6. **Experimental compiler.** Safe mode, capture checking and separation checking are experimental in a nightly;
   their soundness is not proven, and changes between versions (3.10.1 vs. the nightly) show it.

## Benchmark: okay-safe vs okay-scala 0.1.1

`sbt bench/run` (one JVM, `-Xmx2g`, 10 warm-up runs then 15 timed; min and median ms; machine under other load,
load average ~6). Two runs:

| program | okay-scala 0.1.1 | okay-safe |
|---|---|---|
| state loop, n = 10^6 (get + put per turn, one handler outside) | 14.9–15.5 / 16.1–17.8 | 15.0–17.0 / 16.1–17.5 |
| one handler outside, n = 10^6 asks | 6.6–7.2 / 7.0–7.7 | 7.4–7.7 / 7.9–8.3 |
| a handler around the rest each step, n = 10^6 (10^6 levels deep) | 36.3–36.4 / 82–123 | 48.9–49.6 / 238–240 |
| `run()` inside each continuation, `-Xss512k` | n=100 ok; **n=1000 StackOverflowError** (default stack: 1000 ok, 10 000 overflows) | **refused**: no program in safe mode can write it; at run time `NestedRun` at the first step, any n |

The safe kernel is on par for the loop and within ~10% one handler outside; a handler around the rest each step is
~35% slower at its best (minimum) and the median is dominated by collection of the million levels. The third
nesting is not a cost but a refusal: what overflows okay-scala's stack is not a program okay-safe accepts.
