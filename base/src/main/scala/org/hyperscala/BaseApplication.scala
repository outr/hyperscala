package org.hyperscala

import scala.language.experimental.macros

trait BaseApplication {
  protected[hyperscala] var picklers: Vector[Pickler[_]]
  protected[hyperscala] var _screens: Vector[Screen]

  protected def createApplicationManager(): ApplicationManager = macro BaseMacros.applicationManager
  protected[hyperscala] def add[T](pickler: Pickler[T]): Unit
}
