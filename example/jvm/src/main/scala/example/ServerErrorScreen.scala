package example

import java.io.File

import io.undertow.util.StatusCodes
import org.hyperscala.delta.{Delta, Selector}
import org.hyperscala.{Request, RequestValidator, ServerScreen}

trait ServerErrorScreen extends ErrorScreen with ServerScreen {
  override def template: File = new File("src/main/web/error.html")

  override def deltas(request: Request): List[Delta] = request.exchange match {
    case Left(exchange) => {
      val message: String = Option(exchange.getRequestHeaders.get(RequestValidator.HeaderKey)).map(_.getFirst)
        .getOrElse(StatusCodes.getReason(exchange.getStatusCode))
      List(
        Delta.ReplaceContent(Selector.ById("title"), message),
        Delta.ReplaceContent(Selector.ById("subtitle"), s"Status: ${exchange.getStatusCode}")
      )
    }
    case Right(exchange) => throw new RuntimeException(s"Error Screen not supported in WebSocket.")
  }
}
