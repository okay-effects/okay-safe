package okay.safe

import okay.safe.std.*

/** files a program opens by name, writes and reads by id: the files themselves are the handler's */
enum Files[X]:
  case Open(name: String) extends Files[Id[Files]]
  case Write(file: Id[Files], text: String) extends Files[Unit]
  case Read(file: Id[Files]) extends Files[String]
  case Close(file: Id[Files]) extends Files[Unit]

object Files:
  def open(name: String) = word(Open(name))
  def write(file: Id[Files], text: String) = word(Write(file, text))
  def read(file: Id[Files]) = word(Read(file))
  def close(file: Id[Files]) = word(Close(file))

/** DYNAMIC RESOURCES BY ID: the real resource in the handler's table, the program given an id; a closed id, or
 * another table's, refused; under a handler that resumes many times, one table for every path, nothing replayed */
class Resources extends munit.FunSuite:
  /** a file: what the host holds, and what it did */
  final class File(val name: String):
    val text = StringBuilder()

  /** the handler of `Files`, holding `table`: an id it cannot find is refused, a failure of the program */
  def files[R[_], A](table: Table[Files, File])(using In[Throws % String, R]): Handler[Files, R, A, A] =
    Authority.general[Files, R, A, A](pure)([X] => (op: Files[X], k: X => A ! R) =>
      def found(id: Id[Files])(use: File => A ! R): A ! R = table.get(id) match
        case Some(f) => use(f)
        case None => Throws.raise[String](s"no file $id: closed, or not this handler's").into[R]
      op match
        case Files.Open(name) => k(table.add(File(name)))
        case Files.Write(id, text) => found(id)(f => k(f.text.append(text): Unit))
        case Files.Read(id) => found(id)(f => k(f.text.toString))
        case Files.Close(id) => table.remove(id) match
          case Some(_) => k(())
          case None => Throws.raise[String](s"no file $id: closed already").into[R])

  type Out[X] = (Throws % String)[X]
  type App[X] = (Throws % String + Files)[X]

  def ran[A](p: A ! App, table: Table[Files, File]): Either[String, A] =
    run(Throws.run(handle(p, files[Out, A](table))))

  test("opened, used by its id, closed: the program never holds the file"):
    val table = Table[Files, File]()
    val p: String ! App =
      for f <- Files.open("log"); _ <- Files.write(f, "a"); _ <- Files.write(f, "b"); s <- Files.read(f)
          _ <- Files.close(f) yield s
    assertEquals(ran(p, table), Right("ab"))
    assertEquals(table.size, 0)

  test("a closed id is refused: used after its close, closed twice"):
    val table = Table[Files, File]()
    val used: String ! App = for f <- Files.open("log"); _ <- Files.close(f); s <- Files.read(f) yield s
    assert(ran(used, table).left.exists(_.contains("closed, or not this handler's")), ran(used, table).toString)
    val twice: Unit ! App = for f <- Files.open("log"); _ <- Files.close(f); _ <- Files.close(f) yield ()
    assert(ran(twice, table).left.exists(_.contains("closed already")))

  test("another table's id is refused: ids are unique in the JVM"):
    val mine = Table[Files, File]()
    val theirs = Table[Files, File]()
    val id = ran(Files.open("theirs").into[App], theirs).fold(fail(_), identity)
    assertEquals(ran(Files.read(id).into[App], mine), Left(s"no file $id: closed, or not this handler's"))
    assertEquals(theirs.drain().map(_.name), List("theirs"))

  test("multi-shot: one table for every path; open ran once; a close on one path is a close for the next"):
    type Many[X] = (Choose + Throws % String + Files)[X]
    val table = Table[Files, File]()
    val counting = Table[Files, File]()
    // the file opened before the choice: both paths write to the same file, which is not copied
    val shared: String ! Many =
      for f <- Files.open("log"); x <- Choose.among("1", "2"); _ <- Files.write(f, x); s <- Files.read(f) yield s
    def all[A](p: A ! Many, t: Table[Files, File]) =
      run(Throws.run(handle(Choose.all(p), files[Out, List[A]](t))))
    assertEquals(all(shared, table), Right(List("1", "12")))
    assertEquals(table.drain().map(f => (f.name, f.text.toString)), List(("log", "12")))
    // the first path closes it: the second is refused — the failure ends the run, as Throws outside Choose does
    val closing: String ! Many =
      for f <- Files.open("log"); x <- Choose.among("1", "2"); _ <- Files.write(f, x)
          _ <- if x == "1" then Files.close(f).into[Many] else unit[Many] yield x
    assert(all(closing, counting).left.exists(_.contains("closed, or not this handler's")))
    assertEquals(counting.size, 0)
    // the open after the choice: each path opens its own, once — two files, the open not replayed
    val each: String ! Many =
      for x <- Choose.among("1", "2"); f <- Files.open(s"log$x"); _ <- Files.write(f, x); s <- Files.read(f) yield s
    val apart = Table[Files, File]()
    assertEquals(all(each, apart), Right(List("1", "2")))
    assertEquals(apart.drain().map(f => (f.name, f.text.toString)), List(("log1", "1"), ("log2", "2")))
