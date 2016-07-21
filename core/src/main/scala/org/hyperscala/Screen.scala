package org.hyperscala

import pl.metastack.metarx.Channel

import scala.language.experimental.macros

trait Screen {
  app._screens = app._screens :+ this

  def app: WebApplication

  def register[T]: Channel[T] = macro Macros.pickler[T]

  def isPathMatch(path: String): Boolean
}