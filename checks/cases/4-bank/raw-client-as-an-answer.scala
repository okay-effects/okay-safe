// THE BOUNDARY: the raw client as the answer of an effect declared for it. Safe mode reads a parent's type
// arguments: it refuses this itself. (raw-client-as-a-readers-answer: the same through an effect of the kernel's.)
import language.experimental.safe
import okay.safe.*
import java.net.http.HttpClient

enum Net[X]:
  case Connect() extends Net[HttpClient] // error: Cannot refer to class HttpClient
  case Fetch(url: String) extends Net[String]
