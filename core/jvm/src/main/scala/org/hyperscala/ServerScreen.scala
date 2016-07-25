package org.hyperscala

import java.io.File

import com.outr.scribe.Logging
import io.undertow.server.HttpServerExchange
import io.undertow.util.Headers
import org.hyperscala.stream.{Delta, HTMLParser, Selector}

trait ServerScreen extends Screen with ExplicitHandler with Logging {
  lazy val streamable = HTMLParser(template)

  def activate(connection: Connection): Unit
  def deactivate(connection: Connection): Unit

  def template: File

  def deltas(): List[Delta]

  def partialParentId: String

  def partialSelector: Selector

  def html(partial: Boolean): String = {
    val selector = if (partial) {
      Some(partialSelector)
    } else {
      None
    }
    streamable.stream(deltas(), selector)
  }

  override def handleRequest(exchange: HttpServerExchange): Unit = {
    val partial = Option(exchange.getQueryParameters.get("partial")).exists(_.contains("true"))
    logger.info(s"handleRequest: ${exchange.getQueryParameters} / $partial")
    val html = this.html(partial)
    exchange.getResponseHeaders.put(Headers.CONTENT_LENGTH, html.length)
    exchange.getResponseHeaders.put(Headers.CONTENT_TYPE, "text/html")
    exchange.getResponseSender.send(html)
  }
}