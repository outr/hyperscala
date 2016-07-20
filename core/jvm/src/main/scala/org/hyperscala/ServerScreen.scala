package org.hyperscala

import java.io.File

import io.undertow.server.HttpServerExchange
import org.hyperscala.stream.Delta

trait ServerScreen extends Screen {
  def template: File

  def deltas(exchange: HttpServerExchange): List[Delta]
}