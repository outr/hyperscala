package org

import com.outr.scribe.Logging
import org.scalajs.dom._
import org.scalajs.dom.ext._
import org.scalajs.dom.raw.HTMLElement

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

  implicit class HTMLElementExtras(e: HTMLElement) {
    def byTag[T <: HTMLElement](tagName: String): Vector[T] = {
      e.getElementsByTagName(tagName).toVector.map(_.asInstanceOf[T])
    }
    def byClass[T <: HTMLElement](className: String): Vector[T] = {
      e.getElementsByClassName(className).toVector.map(_.asInstanceOf[T])
    }
  }
}