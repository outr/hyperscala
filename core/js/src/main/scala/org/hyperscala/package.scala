package org

import com.outr.scribe.Logging
import org.scalajs.dom._
import org.scalajs.dom.ext._
import org.scalajs.dom.raw.HTMLElement

import scala.scalajs.js.URIUtils

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

  def completePath: String = document.location.href.substring(document.location.href.indexOf('/', 8))

  def params: Map[String, String] = {
    val href = URIUtils.decodeURI(document.location.href)
    val splitPoint = href.indexOf('?')
    if (splitPoint == -1) {
      Map.empty
    } else {
      val query = href.substring(splitPoint + 1)
      query.split('&').map(param => param.trim.splitAt(param.indexOf('='))).collect {
        case (key, value) if key.nonEmpty => key -> value.substring(1)
        case (key, value) if value.nonEmpty => "query" -> value
      }.toMap
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