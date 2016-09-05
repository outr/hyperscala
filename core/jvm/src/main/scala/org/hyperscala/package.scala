package org

import io.undertow.server.HttpServerExchange
import io.undertow.util.AttachmentKey

import scala.language.implicitConversions

package object hyperscala {
  implicit def screen2ServerScreen(screen: BaseScreen): ServerScreen = screen.asInstanceOf[ServerScreen]

  private val attachmentKey = AttachmentKey.create[ExtendedExchange](classOf[ExtendedExchange])
  // Cache so we don't create multiple for performance reasons
  implicit def exchange2Extended(exchange: HttpServerExchange): ExtendedExchange = Option(exchange.getAttachment[ExtendedExchange](attachmentKey)) match {
    case Some(ee) => ee
    case None => {
      val ee = new ExtendedExchange(exchange)
      exchange.putAttachment(attachmentKey, ee)
      ee
    }
  }

  class ExtendedExchange(exchange: HttpServerExchange) {
    lazy val query: Option[String] = exchange.getQueryString match {
      case null | "" => None
      case q => Some(q)
    }
    lazy val url: URL = {
      val s = query match {
        case Some(q) => s"${exchange.getRequestURL}?$query"
        case None => exchange.getRequestURL
      }
      URL(s)
    }
  }
}