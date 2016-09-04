package org.hyperscala

import java.net.{URLDecoder, URLEncoder}

object Util {
  def decodeURIComponent(value: String): String = URLDecoder.decode(value, "UTF-8")
  def encodeURIComponent(value: String): String = URLEncoder.encode(value, "UTF-8")
}
