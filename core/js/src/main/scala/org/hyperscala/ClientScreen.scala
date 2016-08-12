package org.hyperscala

import org.scalajs.dom._
import pl.metastack.metarx.Sub

trait ClientScreen extends Screen {
  private[hyperscala] var _loaded = false
  def loaded: Boolean = _loaded

  val title: Sub[Option[String]] = Sub(None)

  type Path = String

  def path: Option[Path]

  final def show(): Unit = if (loaded) {
    title.get match {
      case Some(t) => document.title = t
      case None => // No title set
    }
    activate()
  }

  private[hyperscala] def load(content: Option[ScreenContentResponse]): Unit = if (!loaded) {
    content match {
      case Some(c) => {
        title := Option(c.title)
        val parent = document.getElementById(c.parentId)
        val temp = document.createElement("div")
        temp.innerHTML = c.content
        parent.appendChild(temp.firstChild)
      }
      case None => {
        // First load
        title := Option(document.title)
      }
    }
    _loaded = true
    title.attach {
      case Some(t) => if (app.connection.screen.get.contains(this)) {
        document.title = t
      }
      case None => // No title
    }
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
}