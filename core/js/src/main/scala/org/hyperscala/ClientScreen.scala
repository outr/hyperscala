package org.hyperscala

import org.scalajs.dom._
import org.scalajs.dom.ext._
import org.scalajs.dom.raw.HTMLElement

trait ClientScreen extends Screen {
  type URL = String

  protected def init(): Unit

  protected def activate(): URL

  protected def deactivate(): Unit

  def byTag[T <: HTMLElement](tagName: String): List[T] = {
    document.getElementsByTagName(tagName).toList.map(_.asInstanceOf[T])
  }

  def byId[T <: HTMLElement](id: String): T = document.getElementById(id).asInstanceOf[T]
}