package org.hyperscala.util

import org.scalajs.dom._

object Cookies {
  private lazy val map = document.cookie.split(';').map(s => s.splitAt(s.indexOf('='))).map(t => t._1 -> t._2.substring(1)).toMap

  def get(name: String): Option[String] = map.get(name)
  def apply(name: String): String = get(name).getOrElse(throw new RuntimeException(s"Unable to find '$name' in map: ${map.map(t => s"${t._1} = ${t._2}").mkString("[", ", ", "]")}, cookies: ${document.cookie}"))
}