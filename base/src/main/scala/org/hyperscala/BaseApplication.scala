package org.hyperscala

import com.outr.reactify.Channel

import scala.language.experimental.macros

trait BaseApplication extends Picklers {
  protected def register[T]: Channel[T] = macro BaseMacros.appPickler[T]
}
