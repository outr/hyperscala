package example

import java.io.File

import org.hyperscala.ServerScreen
import org.hyperscala.stream.{ById, ByTag, Delta, Selector}

trait ServerLoginScreen extends LoginScreen with ServerScreen {
  // Authenticate the username / password on the server
  authenticate.attach { auth =>
    logger.info(s"Authentication request: ${auth.username} / ${auth.password} - Connection: ${app.connection}")
    if (auth.username == "user" && auth.password == "password") {
      response := AuthResponse(None)
    } else {
      response := AuthResponse(Some("Invalid username / password combination"))
    }
  }

  override def template: File = new File("src/main/web/login.html")
  override def partialParentId: String = "content"
  override def partialSelector: Selector = ById("loginForm")
  override def deltas(): List[Delta] = List(
    Delta.ReplaceContent(ByTag("title"), "Modified Login Title")
  )
}