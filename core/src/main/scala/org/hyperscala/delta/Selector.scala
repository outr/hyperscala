package org.hyperscala.delta

sealed trait Selector

object Selector {
  case class ById(id: String) extends Selector
  case class ByClass(className: String) extends Selector
  case class ByTag(tagName: String) extends Selector
}