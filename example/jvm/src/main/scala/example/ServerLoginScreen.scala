package example

import java.io.File

import io.undertow.server.HttpServerExchange
import org.hyperscala.stream.{ById, ByTag, Delta, Selector}
import org.hyperscala.{PartialSupport, Request, RequestValidator, Server, ServerScreen, ValidationResult}

trait ServerLoginScreen extends LoginScreen with ServerScreen with PartialSupport with RequestValidator {
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
  override def deltas(request: Request): List[Delta] = List(
    Delta.ReplaceContent(ByTag("title"), "Modified Login Title")
  )

  override def validate(exchange: HttpServerExchange): ValidationResult = {
    if (ExampleServer.session.username.get.isEmpty) {
      logger.info(s"User is not logged in!")
      ValidationResult.Continue
    } else {
      logger.info(s"User is already logged in (${ExampleServer.session.username.get.get}), redirecting to dashboard...")
      ValidationResult.Redirect(ExampleApplication.dashboard.path.get)
    }
  }
}