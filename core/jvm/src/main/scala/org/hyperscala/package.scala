package org

import io.undertow.server.HttpServerExchange

import scala.language.implicitConversions

package object hyperscala {
  implicit def screen2ServerScreen(screen: BaseScreen): ServerScreen = screen.asInstanceOf[ServerScreen]

  implicit class ExtendedExchange(exchange: HttpServerExchange) {
    lazy val query: String = exchange.getQueryString
    lazy val path = exchange.getRequestPath
    lazy val completePath: String = {
      if (query != null && query.nonEmpty){
        s"$path?$query"
      } else {
        path
      }
    }
  }
}