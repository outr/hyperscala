package org.hyperscala

import pl.metastack.metarx.Channel

import scala.language.experimental.macros

trait BaseApplication {
  protected[hyperscala] var picklers: Vector[Pickler[_]]

  protected[hyperscala] def add[T](pickler: Pickler[T]): Unit
  protected[hyperscala] def add(screen: BaseScreen): Unit

  protected def register[T]: Channel[T] = macro BaseMacros.appPickler[T]

  def byPath(path: String): BaseScreen
}
