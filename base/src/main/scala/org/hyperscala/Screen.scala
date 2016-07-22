package org.hyperscala

import pl.metastack.metarx.Channel

import scala.language.experimental.macros

trait Screen {
  app._screens = app._screens :+ this

  def app: BaseApplication

  def register[T]: Channel[T] = macro BaseMacros.pickler[T]

  def isPathMatch(path: String): Boolean
}