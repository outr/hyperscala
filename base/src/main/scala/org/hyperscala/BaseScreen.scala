package org.hyperscala

import pl.metastack.metarx.Channel

import scala.language.experimental.macros

trait BaseScreen {
  app.add(this)

  def app: BaseApplication
  def priority: Int

  protected def register[T]: Channel[T] = macro BaseMacros.pickler[T]

  def isPathMatch(path: String): Boolean
}