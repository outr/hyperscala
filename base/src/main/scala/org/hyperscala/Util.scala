package org.hyperscala

import scala.language.experimental.macros

object Util {
  def encodeURIComponent(value: String): String = macro BaseMacros.encodeURIComponent
  def decodeURIComponent(value: String): String = macro BaseMacros.decodeURIComponent
}
