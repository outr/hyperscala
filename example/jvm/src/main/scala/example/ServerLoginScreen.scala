package example

import java.io.File

import org.hyperscala.{Connection, Server, ServerScreen}
import org.hyperscala.stream.{ById, ByTag, Delta, Selector}

trait ServerLoginScreen extends LoginScreen with ServerScreen {
  // Authenticate the username / password on the server
  authenticate.attach { auth =>
    logger.info(s"Authentication request: ${auth.username} / ${auth.password} - Connection: ${app.connection}, Session: ${Server.session[ExampleSession]}")
    if (auth.username == "user" && auth.password == "password") {
      ExampleServer.session.username := Some(auth.username)
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

  override def activate(connection: Connection): Unit = {
    logger.info(s"Username: ${ExampleServer.session.username.get}")
  }
}