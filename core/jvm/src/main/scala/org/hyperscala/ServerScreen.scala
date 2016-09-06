package org.hyperscala

import java.io.File

import com.outr.scribe.Logging
import io.undertow.server.HttpServerExchange
import io.undertow.util.Headers
import io.undertow.websockets.spi.WebSocketHttpExchange
import org.hyperscala.manager.{ServerApplicationManager, ServerConnection}
import org.hyperscala.stream.{ByTag, Delta, HTMLParser}

trait ServerScreen extends Screen with ExplicitHandler with Logging {
  lazy val streamable = HTMLParser(template)

  protected def establishConnection: Boolean = true

  def activate(connection: ServerConnection): Unit = {}
  def deactivate(connection: ServerConnection): Unit = {}

  def template: File

  def deltas(request: Request): List[Delta]

  def title(): String = streamable.stream(Nil, Some(ByTag("title")), includeTag = false)

  def html(request: Request, partial: Boolean): String = {
    val selector = if (partial) {
      this match {
        case ps: PartialSupport => Some(ps.partialSelector)
        case _ => throw new RuntimeException(s"$this is not an instance of PartialSupport.")
      }
    } else {
      None
    }
    val d = request.exchange match {
      case Left(exchange) if establishConnection => {
        val url = exchange.url
        val appManager = app.appManager.asInstanceOf[ServerApplicationManager]
        val connection = appManager.createConnection(url)
        appManager.using(connection) {
          val input = s"""<input id="hyperscala-connection-id" type="hidden" value="${connection.id}"/>"""
          deltas(request) ::: List(Delta.InsertFirstChild(ByTag("body"), input))
        }
      }
      case _ => deltas(request)
    }
    streamable.stream(d, selector)
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