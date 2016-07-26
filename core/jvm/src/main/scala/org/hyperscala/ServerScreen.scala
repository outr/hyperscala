package org.hyperscala

import java.io.File

import com.outr.scribe.Logging
import io.undertow.server.HttpServerExchange
import io.undertow.util.Headers
import io.undertow.websockets.spi.WebSocketHttpExchange
import org.hyperscala.stream.{Delta, HTMLParser}

trait ServerScreen extends Screen with ExplicitHandler with Logging {
  lazy val streamable = HTMLParser(template)

  def activate(connection: ServerConnection): Unit = {}
  def deactivate(connection: ServerConnection): Unit = {}

  def template: File

  def deltas(request: Request): List[Delta]

  def html(request: Request, partial: Boolean): String = {
    val selector = if (partial) {
      this match {
        case ps: PartialSupport => Some(ps.partialSelector)
        case _ => throw new RuntimeException(s"$this is not an instance of PartialSupport.")
      }
    } else {
      None
    }
    streamable.stream(deltas(request), selector)
  }

  override def handleRequest(exchange: HttpServerExchange): Unit = {
    val partial = Option(exchange.getQueryParameters.get("partial")).exists(_.contains("true"))
    val html = this.html(Request(Left(exchange)), partial)
    exchange.getResponseHeaders.put(Headers.CONTENT_LENGTH, html.length)
    exchange.getResponseHeaders.put(Headers.CONTENT_TYPE, "text/html")
    exchange.getResponseSender.send(html)
  }
}

case class Request(exchange: Either[HttpServerExchange, WebSocketHttpExchange])