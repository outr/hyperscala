package org.hyperscala

import pl.metastack.metarx.Channel

import scala.language.experimental.macros

trait BaseApplication {
  protected[hyperscala] def add[T](pickler: Pickler[T]): Unit

  protected def register[T]: Channel[T] = macro BaseMacros.appPickler[T]
}