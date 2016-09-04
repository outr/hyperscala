package org.hyperscala

import scala.scalajs.js.URIUtils

object Util {
  def decodeURIComponent(value: String): String = URIUtils.decodeURIComponent(value)
  def encodeURIComponent(value: String): String = URIUtils.encodeURIComponent(value)
}
