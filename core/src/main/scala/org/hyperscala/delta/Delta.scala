package org.hyperscala.delta

trait Delta {
  def selector: Selector
}

object Delta {
  def Replace(selector: Selector, content: => String): Replace = new Replace(selector, () => content)
  def Process(selector: Selector, replace: Boolean, onlyOpenTag: Boolean, processor: String => String): Processor = new Processor(selector, replace, onlyOpenTag, processor)
  def InsertBefore(selector: Selector, content: => String): InsertBefore = new InsertBefore(selector, () => content)
  def InsertFirstChild(selector: Selector, content: => String): InsertFirstChild = new InsertFirstChild(selector, () => content)
  def ReplaceContent(selector: Selector, content: => String): ReplaceContent = new ReplaceContent(selector, () => content)
  def ReplaceAttribute(selector: Selector, attributeName: String, content: => String): ReplaceAttribute = new ReplaceAttribute(selector, attributeName, () => content)
  def InsertLastChild(selector: Selector, content: => String): InsertLastChild = new InsertLastChild(selector, () => content)
  def InsertAfter(selector: Selector, content: => String): InsertAfter = new InsertAfter(selector, () => content)
  def Template(selector: Selector, deltas: List[Delta]): Template = new Template(selector, deltas)
}

case class Replace(selector: Selector, content: () => String) extends Delta
case class ReplaceAttribute(selector: Selector, attributeName: String, content: () => String) extends Delta
case class Processor(selector: Selector, replace: Boolean, onlyOpenTag: Boolean, processor: String => String) extends Delta
case class InsertBefore(selector: Selector, content: () => String) extends Delta
case class InsertFirstChild(selector: Selector, content: () => String) extends Delta
case class ReplaceContent(selector: Selector, content: () => String) extends Delta
case class InsertLastChild(selector: Selector, content: () => String) extends Delta
case class InsertAfter(selector: Selector, content: () => String) extends Delta
case class Template(selector: Selector, deltas: List[Delta]) extends Delta