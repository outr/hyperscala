package org.hyperscala.delta

import org.scalajs.dom._
import org.hyperscala._

object BrowserDelta {
  def apply(deltas: List[Delta], selector: Option[Selector] = None): Unit = {
    val in: html.Element = selector.map(lookup(_).head).getOrElse(document.body)
    deltas.foreach { delta =>
      val elements = lookup(delta.selector, in)
      elements.foreach { e =>
        modify(delta, e)
      }
    }
  }

  def lookup(selector: Selector, in: html.Element = document.body): Vector[html.Element] = selector match {
    case Selector.ByClass(className) => in.byClass[html.Element](className)
    case Selector.ById(id) => Vector(byId[html.Element](id))
    case Selector.ByTag(tagName) => in.byTag[html.Element](tagName)
  }

  def modify(delta: Delta, e: html.Element): Unit = delta match {
    case InsertAfter(selector, content) => e.parentElement.insertBefore(content().toElement[html.Element], e.nextSibling)
    case InsertBefore(selector, content) => e.parentElement.insertBefore(content().toElement[html.Element], e)
    case InsertFirstChild(selector, content) => if (e.hasChildNodes()) {
      e.insertBefore(content().toElement[html.Element], e.firstChild)
    } else {
      e.appendChild(content().toElement[html.Element])
    }
    case InsertLastChild(selector, content) => e.appendChild(content().toElement[html.Element])
    case Processor(selector, replace, onlyOpenTag, processor) => throw new UnsupportedOperationException("Processor not yet supported.")
    case Replace(selector, content) => e.parentElement.replaceChild(content().toElement[html.Element], e)
    case ReplaceAttribute(selector, attributeName, content) => e.setAttribute(attributeName, content())
    case ReplaceContent(selector, content) => e.innerHTML = content()
    case Template(selector, deltas) => throw new UnsupportedOperationException("Template not yet supported.")
  }
}