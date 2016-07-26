package org.hyperscala
import io.undertow.server.HttpServerExchange
import io.undertow.util.{Headers, HttpString, StatusCodes}

trait RequestValidator extends ServerScreen {
  def validate(exchange: HttpServerExchange): ValidationResult

  override def handleRequest(exchange: HttpServerExchange): Unit = validate(exchange) match {
    case ValidationResult.Continue => super.handleRequest(exchange)
    case ValidationResult.Redirect(location) => {
      exchange.setStatusCode(StatusCodes.FOUND)
      exchange.getResponseHeaders.put(Headers.LOCATION, location)
      exchange.endExchange()
    }
    case ValidationResult.Error(status, message) => {
      exchange.setStatusCode(status)
      exchange.getResponseHeaders.put(new HttpString("ErrorMessage"), message)
    }
  }
}

sealed trait ValidationResult

object ValidationResult {
  case object Continue extends ValidationResult
  case class Redirect(location: String) extends ValidationResult
  case class Error(status: Int, message: String) extends ValidationResult
}