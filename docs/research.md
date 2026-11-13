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
that says `package okay.safe` — or any package under it. Exceptions are allowed. Three holes once open at compile
time are closed by the kernel's own design: a client's API is `private` to the host's package (safe mode does not
follow a parameter's instance members), a cell is read and written only by update methods (a cell that escapes is
read-only, so inert), and a runner is given once by the kernel's `Launch` to the application's `Entry`, never made.

Adding a **whole-program check of the TASTy** (module `whole`: default-deny allowlist, transitive over the program
and every dependency given with it, no `throw`/`try`, no `@assumeSafe` outside safe mode, no class in the kernel's
packages, capture checking and explicit nulls read from each unit's TASTy attributes) closes every hole found here
that is visible in the code. Adding **JPMS** (the kernel a named module) and the JVM flag
`--sun-misc-unsafe-memory-access=deny` closes the split package and `Unsafe` at run time as well. And a **seal**
(module `gate`) makes the configuration part of the artifact: the gate compiles with the required flags, runs the
check, and records it all with every file's hash; the kernel's `Launch` refuses to start what does not match it, a
JVM without the flag, or a kernel off the module path. The same gate, in front of a runner, takes **code an agent
wrote** (module `agent`): compiled, checked, and only then run with the host's handlers and its policy.

What remains — what no check here blocks — is listed under "Where full guarantees stop": divergence and resource
exhaustion, exceptions the runtime throws by itself, the trusted base (the kernel's machine and launcher, the
standard library as allowlisted, the JVM), the host — the edge where programs are run and authorities given is
ordinary code, capture checked but trusted —, and the seal's author: unsigned, a seal proves what was built, not
who built it.

## The guarantee, as it stands

> A program — every unit outside the host's packages, its dependencies included — compiled in safe mode with
> capture and separation checking, and passing the whole-program check with the default policy, is a value: it
> performs no effect when built; run, it performs effects only by asking operations, which only handlers answer;
> a handler holding an authority (a client, the clock) is made only by the host; a sensitive effect is answered only
> under its audit; a runner exists only in the entry `Launch` gave it to, after verifying the deployment's seal;
> a handler's state never leaves its clause readable; continuations and clauses are pure functions, so a
> continuation resumed twice is the same computation twice — never a replay, since `k` is immutable data. Excluded:
> non-termination, stack or heap exhaustion, exceptions thrown by the runtime itself (arithmetic, a partial function
> of the standard library), and whatever the trusted base does.

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
| sealed deployment | `java @app.args` (the flag, `--module-path mods --add-modules okay.safe -jar app.jar`): written by the gate, verified by `Launch` |

Scala 3.10.1 (`sbt -Dscala=3.10.1 test`): the kernel and its 87 tests pass; two cases differ — a polymorphic clause
that assigns `k` or its cell to a variable it closes over **compiles** under 3.10.1 and is refused by the nightly.
The guarantee above is the nightly's.

## The kernel's layers

| file | compiled | who may name it |
|---|---|---|
| `Effects.scala`, `Audit.scala`, `std/*` | safe mode + separation checking | everyone |
| `Rows.scala`, `Machine.scala`, `Messages.scala`, `Branches.scala` | plain, `@assumeSafe` (trusted base) | everyone; the machine is `private[safe]` |
| `Host.scala` — `Runner` (made by the kernel only), `Entry`, `run`, `step`, `NestedRun` | capture checked, not safe mode, not `@assumeSafe` | the host only |
| `Launch.scala` — the application's entry and the seal's verification | no checks; `private[safe]`: the JVM starts it, no code names it | nobody: `Main-Class` |
| `Unchecked.scala` — `Authority` (handlers holding a capability, the system clock, `pull`) | no checks | the host only |

The split of the host into a capture-checked part (`Host.scala`) and an unchecked part (`Unchecked.scala`) is what
lets the host's own code be capture checked — a host continuation that captures the runner or the client does not
compile — while the handlers that must hold a capability inside a polymorphic pure clause (which capture checking
cannot express: `unsafeAssumePure` strips the outer capture set, not the inner one) are made where nothing is
checked, and their capabilities are hidden from the program's type.

## The matrix

Mechanisms: **CC** capture/separation checking; **SM** safe mode; **WP** the whole-program TASTy check; **RT** run
time (the machine's own check, JPMS, a JVM flag); **—** not blocked. Each row names its case(s) in `checks/cases`
or its test in `checks/src/test` (RuntimeTest, WholeTest, SealTest), or its program in `agent/programs` (AgentTest).
**access** is Scala's access control (`private`, `private[pkg]`), checked by the typer.

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
| 7 | host declared under `okay.safe` (`private[safe]` reaches subpackages): makes a runner | — (Scala's access) | WP: kernel's package, the host's units too | — (JPMS: another package) | host-under-the-kernels-package |
| 7 | `package okay.safe { ... }` nested in a program's file | — | WP: every package clause | — | nested-package-into-the-kernel |
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
| 11 | a class changed after the checks | — | — | `Launch`: `does not match its seal` | SealTest |
| 11 | a class added after the checks; one in the kernel's package | — | — | `Launch`: `not in the seal`; `in the kernel's package` | SealTest |
| 11 | no seal: built without the gate | — | — | `Launch`: `no seal` | SealTest |
| 11 | the JVM without `--sun-misc-unsafe-memory-access=deny` | — | — | `Launch`: tried, `allows sun.misc.Unsafe's memory access` | SealTest |
| 11 | the kernel on the class path; another kernel than the one sealed | — | — | `Launch`: the class path alone; `not the one sealed` (by SHA-256) | SealTest |
| 11 | a source without `import language.experimental.safe` | — | WP: the gate seals nothing | — | SealTest |
| 11 | built without a required flag (`-Werror`, `-Wsafe-init`) | — | — | `Launch`: `compiled without -Wsafe-init` | SealTest |
| 11 | a seal written by hand over a class the gate never saw | — | — | **— (unsigned: accepted)** | SealTest |
| 12 | agent: console, `run`, a cast, reflection, a thread, `Authority`, `caps.unsafe` | SM | (not reached) | — | console, runs-a-program, cast, reflection, thread, authority, escape-hatch |
| 12 | agent: the bank's client called; made | access; SM (`new` of a host class) | — | — | calls-the-client, makes-a-client |
| 12 | agent: the bank handled by the program itself | macro: `the effect Bank is sensitive` | — | — | handles-the-bank |
| 12 | agent: global mutable state | CC | — | — | global-state |
| 12 | agent: `java.time.Instant.now`, `scala.util.Random`, `throw`, a unit in `okay.bank.host` | — | WP | — | java-time, random, throws, kernel-package |
| 12 | agent: no safe import | SM: the gate's own file cannot name it | (WP: not compiled in safe mode) | — | no-import |
| 12 | agent: a transfer over the limit; to a new payee | compiles, passes the gate | — | the guard: blocked, the bank never asked; asked and approved: passed | over-the-limit, new-payee; AgentTest |
| 12 | positive: balance, log, a transfer the guard passes | compiles | clean | runs, audited | good; AgentTest |

## Why each mechanism stops where it does

- **Capture checking** tracks capabilities: what a closure holds. A continuation is `X -> B ! R`, a clause
  `[X] -> (E[X], Continuation) -> ...`, so neither holds a runner, a client, a label, a variable or a writable cell.
  It does not track what is not a capability: a Java object, a static method, an exception, a `null` (which is
  pure). Polymorphic functions were a gap until recently (3.10.1 misses the variable case).
- **Separation checking** makes a cell `Mutable`: an update method asks the exclusive reference; a reference that
  escapes through a type argument is read-only. It does not stop the escape itself — the cell still leaves, as an
  identity — so the kernel makes reading an update too: what escapes can be neither read nor written. A cell given
  away typed exclusive (`Cell[Int]^` in the effect's answer) stands for a root capability no program may use.
- **Access control** is the typer's, and it reaches where safe mode does not: an instance member of a value the
  program was given. So the client's API is `private[host]` and a runner's constructor `private`; but a qualified
  `private[p]` is open to every package under `p`, so no unit may declare under the kernel's package (WP, and
  `Launch` refuses a jar with one).
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
2. **A cell still escapes — inert.** `cell-as-answer` compiles up to the read: the program holds the cell's
   identity (`eq`, `hashCode`), no state. Refusing the escape itself would need the answer type of every effect a
   stateful handler takes to be checked for cells (a macro, as `Plain` is) — not done.
3. **The trusted base**: the machine and rows (`@assumeSafe`, plain Scala), `Launch` and its seal verification, the
   standard library behind the allowlist, the JVM. The audit is as good as the kernel's code that enforces it.
4. **The host.** Running programs and giving authorities is the host's; host code can be capture checked (and here
   is) but is not held to the rules of programs. It can no longer make a runner — it has the one its entry was
   given — but a host *not* capture checked can still use that runner inside a continuation (RuntimeTest 1): only
   `NestedRun` stops that, at run time. Capture checking the host is the host's choice; the seal does not record it.
5. **Configuration, sealed — not signed.** The gate records the compiler, the flags, the policy's hash, the check
   and its result, the JVM flag and the module jars, and `Launch` refuses a deployment that does not match them. But
   the seal is unsigned: anyone who can write the jar can recompute the hashes over classes compiled any way and
   write "clean" (SealTest, last test). A signature by the gate's key, its public half pinned in the kernel's
   launcher, would bind the seal to the gate — then only the gate's key forges. Also: the policy hash is recorded,
   not compared with a pinned one; `-Dokay.safe.unsealed` starts anything (the operator's switch, said on stderr,
   as any JVM flag is the operator's); the JVM flags are checked by trying `Unsafe`, which cannot be set from a jar's
   manifest (`Add-Opens`/`Launcher-Agent-Class` open or add, none denies), so `app.args` carries them.
6. **Agents' programs** pass the same gate, so the same limits: they may diverge or exhaust memory (no time or
   step limit), and the gate itself runs unsealed from sbt (`agent/run`), the kernel on the class path — sealing the
   gate's host, as the bank's is, is the step left.
7. **Experimental compiler.** Safe mode, capture checking and separation checking are experimental in a nightly;
   their soundness is not proven, and changes between versions (3.10.1 vs. the nightly) show it.

## The sealed deployment

`gate/.../Seal.scala` builds it from sources; `core/.../Launch.scala` verifies it (SealTest does both, in JVMs of
their own):

```
sbt "gate/runMain okay.safe.gate.Main --sources example/src/main/scala --host okay.bank.host \
  --entry okay.bank.host.BankHost --kernel core/target/.../classes --library <scala-library.jar> --out deploy"
cd deploy && java @app.args     # --sun-misc-unsafe-memory-access=deny --module-path mods --add-modules okay.safe
                                # -jar app.jar   (Main-Class: okay.safe.Launch)
```

The seal (`META-INF/okay-safe.seal` in app.jar): `okay-safe-seal 1`, `compiler`, `flags`, `policy` (SHA-256 of
the policy, one line an entry), `whole` (the check's version) and `result`, `host`, `entry`, `jvm`, `module
okay.safe <sha>`, `library scala.library <sha>`, and `file <sha> <path>` for every class and TASTy file. `Launch`
asks: the class path the sealed jar alone; every file sealed and unchanged, none added, none under `okay/safe/`;
the format, `-Werror` and `-Wsafe-init` among the flags, a clean result, the JVM flag asked; `sun.misc.Unsafe`'s
memory access refused (tried on a field of the kernel's); the kernel the named module `okay.safe`, closed, and both
module jars the ones sealed. Then, once, it gives the entry a runner.

## A gate for agents' code

`agent/`: an agent writes `object Task: val program: String ! Tools = ...` over `Tools = Log + Clock + Bank`. The
gate compiles it with the required flags beside its own safe-mode file
`GateEntry.program: String ! Tools = Task.program`
(so the compiler fixes the type), checks the whole of it with a policy that adds the bank's and the agent's API and
closes their packages and hosts, and only then loads it and runs it: the log kept, the system's clock, the bank's
client behind `Guard.bank` — an audit whose clause asks the `Guard` effect for a decision on each call, as okay-guard
sees a call (a tool's name and its arguments). The host answers `Guard` with a handler: here rules in okay-guard's
shape (`block bank.transfer amount > 500 : over the limit`, `ask …`, `pass *`, the first that matches decides);
okay-guard itself — its page, its person in the loop, its journal — would be another handler of the same effect.

```
sbt "agent/run agent/programs/good.scala"         # passed the gate (compiled in ~1 s, checked in ~0.1 s), ran
sbt "agent/run agent/programs/java-time.scala"    # REFUSED: java.time.Instant.now: the time now: an ambient effect
```

Compile time per program (AgentTest, one JVM, the compiler warm after the first): 35–300 ms to compile (the first
~700 ms), 20–100 ms to check; a fresh JVM from the command line: 0.8–1.3 s to compile, ~0.1 s to check. A refusal
by the compiler costs no check. Of `agent/programs`: three pass the gate (one runs; two run and are blocked by the
guard before the bank is asked, and one of them passes when the ask is approved), sixteen are refused, each with the
reason in the matrix (row 12).

## What changed (2026-11-13, second pass)

- **Compile time instead of run time or not at all:** a program given the client cannot call it (access, was WP
  only); a cell given away cannot be read (was not blocked); a host continuation cannot make a runner (was
  `NestedRun` only) — `Runner.host` is the kernel's, `Launch` the only caller, `Entry` the only receiver.
- **The application moved** from `okay.safe.example` to `okay.bank`: `private[safe]` reaches every package under
  `okay.safe`, so a host there could make a runner. The whole-program check now refuses any unit under the kernel's
  packages, the host's too, and reads nested package clauses (it read the file's first only); `throw` is reported
  as itself (it was reported as `<special-ops>.throw`, not allowlisted).
- **The seal** and **the agents' gate**, above.

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
