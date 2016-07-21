package org.hyperscala

import pl.metastack.metarx.Channel

import scala.language.experimental.macros

trait Screen {
  def app: WebApplication

  def register[T]: Channel[T] = macro Macros.pickler[T]

  def isPathMatch(path: String): Boolean
}