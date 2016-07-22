package example

import java.io.File

import io.undertow.server.HttpServerExchange
import org.hyperscala.ServerScreen
import org.hyperscala.stream.{ById, ByTag, Delta, Selector}

trait ServerLoginScreen extends LoginScreen with ServerScreen {
  authenticate.attach { auth =>
    logger.info(s"Authentication request: ${auth.username} / ${auth.password} - Connection: ${app.connection}")
    if (auth.username == "user" && auth.password == "password") {
      response := AuthResponse(None)
    } else {
      response := AuthResponse(Some("Invalid username / password combination"))
    }
  }

  override def template: File = new File("src/main/web/login.html")
  override def partialSelector: Selector = ById("login")
  override def deltas(exchange: HttpServerExchange): List[Delta] = List(
    Delta.ReplaceContent(ByTag("title"), "Modified Login Title")
  )
}