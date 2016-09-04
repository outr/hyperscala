package org.hyperscala

import pl.metastack.metarx.Channel

import scala.language.experimental.macros

trait BaseScreen {
  protected def register[T]: Channel[T] = macro BaseMacros.screenPickler[T]
}