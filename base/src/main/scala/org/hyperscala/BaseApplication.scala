package org.hyperscala

import pl.metastack.metarx.Channel

import scala.language.experimental.macros

trait BaseApplication {
  protected[hyperscala] def add[T](pickler: Pickler[T]): Int

  protected def register[T]: Channel[T] = macro BaseMacros.appPickler[T]
}