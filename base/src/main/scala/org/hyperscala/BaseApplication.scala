package org.hyperscala

import pl.metastack.metarx.Channel

import scala.language.experimental.macros

trait BaseApplication {
  protected[hyperscala] var picklers: Vector[Pickler[_]]

  protected def createApplicationManager(): ApplicationManager = macro BaseMacros.applicationManager
  protected[hyperscala] def add[T](pickler: Pickler[T]): Unit
  protected[hyperscala] def add(screen: BaseScreen): Unit

  protected def register[T]: Channel[T] = macro BaseMacros.pickler[T]

  def encodeURIComponent(value: String): String = macro BaseMacros.encodeURIComponent

  def byPath(path: String): BaseScreen
}
