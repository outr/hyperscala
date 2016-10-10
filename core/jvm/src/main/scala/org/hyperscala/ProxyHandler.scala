package org.hyperscala
import java.net.URI

import io.undertow.Handlers
import io.undertow.server.HttpServerExchange
import io.undertow.server.handlers.proxy.SimpleProxyClientProvider

trait ProxyHandler extends Handler {
  def uri: URI

  private lazy val proxyClient = new SimpleProxyClientProvider(uri)
  private lazy val proxyHandler = Handlers.proxyHandler(proxyClient)

  override def handleRequest(url: URL, exchange: HttpServerExchange): Unit = {
    proxyHandler.handleRequest(exchange)
  }
}

object ProxyHandler {
  def apply(matcher: URL => Boolean, uri: String, priority: Priority = Priority.Normal): ProxyHandler = {
    val u = new URI(uri)
    val p = priority
    new ProxyHandler {
      override def uri: URI = u

      override def isURLMatch(url: URL): Boolean = matcher(url)

      override def priority: Priority = p
    }
  }
}