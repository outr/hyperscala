package org.hyperscala
import io.undertow.server.HttpServerExchange
import io.undertow.util.{Headers, HttpString, StatusCodes}

trait RequestValidator extends ServerScreen {
  def validate(exchange: HttpServerExchange): ValidationResult

  override def handleRequest(url: URL, exchange: HttpServerExchange): Unit = validate(exchange) match {
    case ValidationResult.Continue => super.handleRequest(url, exchange)
    case ValidationResult.Redirect(location) => {
      exchange.setStatusCode(StatusCodes.FOUND)
      exchange.getResponseHeaders.put(Headers.LOCATION, location)
      exchange.endExchange()
    }
    case ValidationResult.Error(status, message) => {
      exchange.setStatusCode(status)
      exchange.getResponseHeaders.put(new HttpString(RequestValidator.HeaderKey), message)
    }
  }
}

object RequestValidator {
  val HeaderKey = "ErrorMessage"
}

sealed trait ValidationResult

object ValidationResult {
  case object Continue extends ValidationResult
  case class Redirect(location: String) extends ValidationResult
  case class Error(status: Int, message: String) extends ValidationResult
}