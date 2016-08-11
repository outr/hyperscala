package org.hyperscala

import org.scalajs.dom._
import org.scalajs.dom.ext._
import org.scalajs.dom.raw.HTMLElement

trait ClientScreen extends Screen {
  private[hyperscala] var _loaded = false
  def loaded: Boolean = _loaded

  type Path = String

  def path: Option[Path]

  final def show(): Unit = if (loaded) {
    activate()
  }

  private[hyperscala] def load(content: Option[ScreenContent]): Unit = if (!loaded) {
    content match {
      case Some(c) => {
        val parent = document.getElementById(c.parentId)
        val temp = document.createElement("div")
        temp.innerHTML = c.content
        parent.appendChild(temp.firstChild)
      }
      case None => // First load
    }
    _loaded = true
    init()
    if (app.connection.screen.get.contains(this)) {
      activate()
    } else {
      deactivate()
    }
  }

  def init(): Unit

  def activate(): Unit

  def deactivate(): Unit

  def byTag[T <: HTMLElement](tagName: String): Vector[T] = {
    document.getElementsByTagName(tagName).toVector.map(_.asInstanceOf[T])
  }
  def byClass[T <: HTMLElement](className: String): Vector[T] = {
    document.getElementsByClassName(className).toVector.map(_.asInstanceOf[T])
  }

  def byId[T <: HTMLElement](id: String): T = document.getElementById(id).asInstanceOf[T]

  implicit class HTMLElementExtras(e: HTMLElement) {
    def byTag[T <: HTMLElement](tagName: String): Vector[T] = {
      e.getElementsByTagName(tagName).toVector.map(_.asInstanceOf[T])
    }
    def byClass[T <: HTMLElement](className: String): Vector[T] = {
      e.getElementsByClassName(className).toVector.map(_.asInstanceOf[T])
    }
  }
}