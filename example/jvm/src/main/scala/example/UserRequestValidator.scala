package example

import io.undertow.server.HttpServerExchange
import org.hyperscala.{RequestValidator, ValidationResult}

trait UserRequestValidator extends RequestValidator {
  override def validate(exchange: HttpServerExchange): ValidationResult = {
    if (ExampleServer.session.username.get.nonEmpty) {
      logger.info(s"User is logged in as: ${ExampleServer.session.username.get.get}")
      ValidationResult.Continue
    } else {
      logger.info(s"Redirecting to login page.")
      ValidationResult.Redirect(ExampleApplication.login.path)
    }
  }
}