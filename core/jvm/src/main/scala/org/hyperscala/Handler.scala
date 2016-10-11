package org.hyperscala

import io.undertow.server.{HttpHandler, HttpServerExchange}

trait Handler extends Ordered[Handler] {
  /**
    * Returns true if this URL should be handled by this Handler. The handleRequest method will be invoked following
    * a true response.
    *
    * @param url the current URL being handled
    * @return true if this handler is equipped to handle it
    */
  def isURLMatch(url: URL): Boolean

  /**
    * Handles the exchange.
    *
    * @param url the current URL
    * @param exchange the HTTP request
    */
  def handleRequest(url: URL, exchange: HttpServerExchange): Unit

  /**
    * The priority of this handler. A higher priority will be considered before lower priority.
    */
  def priority: Priority

  override def compare(that: Handler): Int = priority.compare(that.priority)
}

object Handler extends HandlerBuilder()