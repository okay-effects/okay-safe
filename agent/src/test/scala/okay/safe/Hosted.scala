package okay.safe

/** THE TESTS' RUNNER. A runner is given only by `Launch`, once, to an application's entry; `Runner.host` is the
 * kernel's. These tests reach it by declaring into the kernel's package — what the whole-program check refuses of a
 * program, and JPMS of anyone when the kernel is the named module `okay.safe` (RuntimeTest, SealTest). */
def hosted[A](body: Runner ?=> A): A = Runner.host(body)
