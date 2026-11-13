// The ambient effects safe mode refuses: neither compiled in safe mode nor assumed safe, or tagged @rejectSafe.
import language.experimental.safe

object Ambient:
  def console(): Unit = println("hello") // error: Cannot refer to method println in object Predef from safe code since it is tagged @rejectSafe
  def console2(): Unit = Console.out.flush() // error: Cannot refer to object Console
  def time(): Long = System.currentTimeMillis() // error: Cannot refer to method currentTimeMillis in object System
  def nanos(): Long = System.nanoTime() // error: Cannot refer to method nanoTime in object System
  def uuid(): String = java.util.UUID.randomUUID().toString // error: Cannot refer to method randomUUID in object UUID from safe code since it is tagged @rejectSafe
  def env(): String = System.getenv("HOME") // error: Cannot refer to method getenv in object System
  def env2(): Map[String, String] = sys.env // error: Cannot refer to method env
  def exit(): Unit = System.exit(1) // error: Cannot refer to method exit in object System
  def exit2(): Nothing = sys.exit(1) // error: Cannot refer to method exit
  def thread(): Unit = Thread.sleep(1) // error: Cannot refer to method sleep in object Thread
  def file(): Boolean = java.io.File("/tmp").exists() // error: Cannot refer to class File in package java.io
  def read(): String = java.nio.file.Files.readString(java.nio.file.Path.of("/etc/hosts")) // error: Cannot refer to method readString in object Files
  def source(): String = scala.io.Source.fromFile("/etc/hosts").mkString // error: Cannot refer to method fromFile in object Source
  def url(): Object = java.net.URI("https://example.com").toURL.openConnection() // error: Cannot refer to class URI in package java.net
  def random(): Int = java.util.Random().nextInt() // error: Cannot refer to class Random in package java.util
  def props(): String = System.getProperty("user.home") // error: Cannot refer to method getProperty in object System
  def runtime(): Unit = Runtime.getRuntime.gc() // error: Cannot refer to method getRuntime in object Runtime
