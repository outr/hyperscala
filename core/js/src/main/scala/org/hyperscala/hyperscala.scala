package org

import com.outr.scribe.Logging
import org.hyperscala.manager.ClientConnection
import org.scalajs.dom.raw.HTMLElement
import org.scalajs.dom.{DOMList, Element, document, html}

import scala.language.implicitConversions

package object hyperscala extends ExtendedElement(None) with Logging {
  def bySelector[T <: Element](selectors: String, root: Option[Element] = None): Vector[T] = {
    root.map(_.querySelectorAll(selectors)).getOrElse(document.querySelectorAll(selectors)).toVector.asInstanceOf[Vector[T]]
  }

  def firstBySelector[T <: Element](selectors: String, root: Option[Element] = None): Option[T] = {
    bySelector[T](selectors, root).headOption
  }

  def oneBySelector[T <: Element](selectors: String, root: Option[Element] = None): T = {
    firstBySelector[T](selectors, root).getOrElse(throw new RuntimeException(s"Unable to find element by selector: $selectors."))
  }

  implicit def connection2ClientConnection(connection: Connection): ClientConnection = connection.asInstanceOf[ClientConnection]

  implicit class StringExtras(s: String) {
    def toElement[T <: html.Element]: T = {
      val temp = document.createElement("div")
      temp.innerHTML = s
      val child = temp.firstChild.asInstanceOf[html.Element]
      child.asInstanceOf[T]
    }
  }

  implicit class ElementExtras(e: Element) extends ExtendedElement(Some(e)) {
    def parentByTag[T <: HTMLElement](tagName: String): Option[T] = findParentRecursive[T](e.asInstanceOf[HTMLElement], (p: HTMLElement) => {
      p.tagName == tagName
    })
    def parentByClass[T <: HTMLElement](className: String): Option[T] = findParentRecursive[T](e.asInstanceOf[HTMLElement], (p: HTMLElement) => {
      p.classList.contains(className)
    })

//    @tailrec    // TODO: figure out why this won't work
    private def findParentRecursive[T <: HTMLElement](e: HTMLElement, finder: HTMLElement => Boolean): Option[T] = Option(e.parentElement) match {
      case None => None
      case Some(p) => if (finder(p)) {
        Some(p.asInstanceOf[T])
      } else {
        findParentRecursive(p, finder)
      }
    }
  }

  implicit def domListToIterator[T](list: DOMList[T]): Iterator[T] = new Iterator[T] {
    private var position = -1

    override def hasNext: Boolean = list.length > position + 1

    override def next(): T = {
      position += 1
      list.item(position)
    }
  }
}

class ExtendedElement(element: Option[Element]) {
  import hyperscala._

  def byTag[T <: Element](tagName: String): Vector[T] = bySelector[T](tagName, element)
  def byClass[T <: Element](className: String): Vector[T] = bySelector[T](s".$className", element)
  def getById[T <: Element](id: String): Option[T] = firstBySelector[T](s"#$id", element)
  def byId[T <: Element](id: String): T = oneBySelector[T](s"#$id", element)
}