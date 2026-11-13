package okay.safe

import language.experimental.captureChecking

/*
 * THE HOST: what only the edge of an application does — run a program, give a handler an authority (a client, a
 * connection, the clock), pull a stream. This file is NOT compiled in safe mode and is not `@assumeSafe`, so code
 * compiled in safe mode cannot name anything here: a program cannot run a program, and cannot make a handler that
 * holds an authority. It is capture checked, so a host written with capture checking cannot capture its `Runner` in
 * a program's continuation either. (docs/research.md, threats 1 and 4)
 */

/** THE AUTHORITY TO RUN PROGRAMS: a capability. `run` and `step` ask it; a continuation is a pure function
 * (`X -> B ! R`), so one that would run a program with it does not compile: "Found: ... ->{runner} ...,
 * Required: ... -> ...". Only `Runner.host` makes one, for its body, and the body cannot return it. */
final class Runner private () extends caps.SharedCapability

object Runner:
  /** a runner for `body`: the host's edge, where programs are run */
  def host[A](body: Runner ?=> A): A = body(using Runner())

/** A run begun inside another run on the same thread: refused at run time (`Machine.run`). What a host not written
 * with capture checking, or a `Runner.host` inside a continuation, can still do; the types refuse it elsewhere. */
final class NestedRun extends RuntimeException(
  "a program was run inside a running program: a continuation or a handler's clause ran the machine again")

/** a computation run to its next question that no handler in it answers */
def step[R[_], A](p: A ! R)(using Runner): Step[R, A] = Machine.run(p)

/** a program with nothing left to ask, run to its value */
def run[A](p: A ! Pure)(using Runner): A = step(p) match
  case Step.Done(a) => a
  case Step.Asked(_, op, _) => throw NoHandler(op)

