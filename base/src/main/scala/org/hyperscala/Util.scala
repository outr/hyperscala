package org.hyperscala

import scala.util.matching.Regex

object Util {
  private val unreservedCharacters = Set('A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P',
    'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z', 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm',
    'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
    '-', '_', '.', '~'
  )

  private val encodedRegex = """%([a-zA-Z0-9]{2})""".r

  def encodeURIComponent(part: String): String = part.map {
    case c if unreservedCharacters.contains(c) => c
    case c => s"%${c.toLong.toHexString.toUpperCase}"
  }.mkString

  def decodeURIComponent(part: String): String = encodedRegex.replaceAllIn(part, (m: Regex.Match) => {
    val code = Integer.parseInt(m.group(1), 16)
    code.toChar.toString
  })
}
