package org.hyperscala

import org.hyperscala.manager.ApplicationManager
import pl.metastack.metarx.Channel

import scala.language.experimental.macros

trait BaseApplication {
  protected[hyperscala] var picklers: Vector[Pickler[_]]

  final def appManager: ApplicationManager = macro BaseMacros.applicationManager
  protected[hyperscala] def add[T](pickler: Pickler[T]): Unit
  protected[hyperscala] def add(screen: BaseScreen): Unit

  protected def register[T]: Channel[T] = macro BaseMacros.appPickler[T]

  def byPath(path: String): BaseScreen
}
