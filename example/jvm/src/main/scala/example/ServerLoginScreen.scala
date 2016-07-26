package example

import java.io.File

import io.undertow.server.HttpServerExchange
import org.hyperscala.{Connection, RequestValidator, Server, ServerScreen, ValidationResult}
import org.hyperscala.stream.{ById, ByTag, Delta, Selector}

trait ServerLoginScreen extends LoginScreen with ServerScreen with RequestValidator {
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

  override def validate(exchange: HttpServerExchange): ValidationResult = {
    if (ExampleServer.session.username.get.isEmpty) {
      logger.info(s"User is not logged in!")
      ValidationResult.Continue
    } else {
      logger.info(s"User is already logged in (${ExampleServer.session.username.get.get}), redirecting to dashboard...")
      ValidationResult.Redirect(ExampleApplication.dashboard.path)
    }
  }
}