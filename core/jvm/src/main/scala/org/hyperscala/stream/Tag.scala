package org.hyperscala.stream

sealed trait Tag

case class OpenTag(tagName: String, attributes: Map[String, Attribute], start: Int, end: Int, close: Option[CloseTag]) extends Tag

case class CloseTag(tagName: String, start: Int, end: Int) extends Tag