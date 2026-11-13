package okay.safe

/** THE TESTS' RUNNER. A `Runner` is made only by `Runner.host`, for its body, and capture checking keeps the body
 * from returning it. These tests are not capture checked — so the runner can be taken out of its body and given to
 * them all. A leak that code compiled without the checks can write: the reason the guarantee is stated of code
 * compiled in safe mode, and the whole-program check finds code that was not (docs/research.md, threat 9). */
given testRunner: Runner = Runner.host(summon[Runner])
