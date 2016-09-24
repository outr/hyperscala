package org.hyperscala

import pl.metastack.metarx.Channel

import scala.language.experimental.macros

trait BaseApplication extends Picklers {
  protected def register[T]: Channel[T] = macro BaseMacros.appPickler[T]
}
