package org.hyperscala

import org.scalajs.dom._
import org.scalajs.dom.ext._
import org.scalajs.dom.raw.HTMLElement

trait ClientScreen extends Screen {
  private[hyperscala] var _loaded = false
  def loaded: Boolean = _loaded

  type URL = String

  def url: URL

  final def show(): Unit = if (loaded) {
    activate()
  }

  private[hyperscala] def load(content: ScreenContent): Unit = if (!loaded) {
    val parent = document.getElementById(content.parentId)
    parent.innerHTML += content.content
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

  def byTag[T <: HTMLElement](tagName: String): List[T] = {
    document.getElementsByTagName(tagName).toList.map(_.asInstanceOf[T])
  }

  def byId[T <: HTMLElement](id: String): T = document.getElementById(id).asInstanceOf[T]
}