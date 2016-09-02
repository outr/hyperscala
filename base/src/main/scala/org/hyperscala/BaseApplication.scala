package org.hyperscala

import pl.metastack.metarx.Channel

import scala.language.experimental.macros

trait BaseApplication {
  protected[hyperscala] var picklers: Vector[Pickler[_]]
  protected[hyperscala] var _screens: Vector[BaseScreen]

  protected def createApplicationManager(): ApplicationManager = macro BaseMacros.applicationManager
  protected[hyperscala] def add[T](pickler: Pickler[T]): Unit

  protected def register[T]: Channel[T] = macro BaseMacros.pickler[T]

  def encodeURIComponent(value: String): String = macro BaseMacros.encodeURIComponent
}
