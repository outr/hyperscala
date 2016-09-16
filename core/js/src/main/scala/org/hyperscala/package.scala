package org

import com.outr.scribe.Logging
import org.hyperscala.manager.ClientConnection
import org.scalajs.dom._
import org.scalajs.dom.ext._
import org.scalajs.dom.raw.HTMLElement

import scala.annotation.tailrec
import scala.language.implicitConversions

package object hyperscala extends Logging {
  def byTag[T <: HTMLElement](tagName: String): Vector[T] = {
    document.getElementsByTagName(tagName).toVector.map(_.asInstanceOf[T])
  }
  def byClass[T <: HTMLElement](className: String): Vector[T] = {
    document.getElementsByClassName(className).toVector.map(_.asInstanceOf[T])
  }

  def byId[T <: HTMLElement](id: String): T = Option(document.getElementById(id).asInstanceOf[T]) match {
    case Some(t) => t
    case None => {
      val message = s"Unable to find element by id '$id'."
      logger.error(message)
      throw new RuntimeException(message)
    }
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

  implicit class HTMLElementExtras(e: HTMLElement) {
    def byTag[T <: HTMLElement](tagName: String): Vector[T] = {
      e.getElementsByTagName(tagName).toVector.map(_.asInstanceOf[T])
    }
    def byClass[T <: HTMLElement](className: String): Vector[T] = {
      e.getElementsByClassName(className).toVector.map(_.asInstanceOf[T])
    }
    def parentByTag[T <: HTMLElement](tagName: String): Option[T] = findParentRecursive[T](e, (p: HTMLElement) => {
      p.tagName == tagName
    })
    def parentByClass[T <: HTMLElement](className: String): Option[T] = findParentRecursive[T](e, (p: HTMLElement) => {
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
}