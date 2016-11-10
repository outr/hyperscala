package org.hyperscala.util

import org.scalajs.dom._

object Cookies {
  lazy val map = document.cookie.split(';').map(s => s.splitAt(s.indexOf('='))).map(t => t._1 -> t._2.substring(1)).toMap

  def get(name: String): Option[String] = map.get(name)
  def apply(name: String): String = map(name)
}