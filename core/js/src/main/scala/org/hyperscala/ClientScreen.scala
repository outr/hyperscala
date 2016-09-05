package org.hyperscala

import org.hyperscala.manager.ClientConnection
import org.scalajs.dom._
import pl.metastack.metarx.{Channel, Sub}

trait ClientScreen extends Screen {
  private[hyperscala] var _loaded = false
  def loaded: Boolean = _loaded

  protected[hyperscala] var activated = false

  val title: Sub[Option[String]] = Sub(None)
  val stateChange: Channel[StateChange] = Channel()

  def onStateChange(stateChangeType: StateChange)(f: => Unit): Unit = {
    stateChange.attach { evt =>
      if (evt eq stateChangeType) {
        f
      }
    }
  }

  final def show(): Unit = if (loaded) {
    title.get match {
      case Some(t) => document.title = t
      case None => // No title set
    }
    doActivate()
  }

  private[hyperscala] def load(content: Option[ScreenContentResponse]): Unit = if (!loaded) {
    val isPage = content match {
      case Some(c) => {
        title := Option(c.title)
        val parent = document.getElementById(c.parentId)
        val temp = document.createElement("div")
        temp.innerHTML = c.content
        parent.appendChild(temp.firstChild)
        false
      }
      case None => {
        // First load
        title := Option(document.title)
        true
      }
    }
    _loaded = true
    title.attach {
      case Some(t) => if (app.connection.screen.get == this) {
        document.title = t
      }
      case None => // No title
    }
    init(isPage)
    stateChange := StateChange.Initialized
    if (!isPage) {
      if (app.connection.screen.get == this) {
        doActivate()
        stateChange := StateChange.Activated
      } else {
        deactivate()
        stateChange := StateChange.Deactivated
      }
    }
  }
  private def doActivate(): Unit = if (!activated) {
    activated = true
    activate() match {
      case Some(pathChange) => {
        val currentPath = app.connection.path.get
        if (pathChange.path != currentPath || pathChange.force) {
          val c = app.connection.asInstanceOf[ClientConnection]
          logger.info(s"Path changing to ${pathChange.path}")
          if (pathChange.replace || app.connection.replace) {
            c.replacePath(pathChange.path)
          } else {
            c.pushPath(pathChange.path)
          }
          c.updateState()
        }
      }
      case None => // No path change requested
    }
  }
  private[hyperscala] def doDeactivate(): Unit = {
    activated = false
    deactivate()
  }

  /**
    * Initializes this screen. Called after the content of the screen has been properly loaded and injected into the
    * page.
    *
    * @param isPage true if this screen represents the loaded page and false if it was dynamically loaded
    */
  protected def init(isPage: Boolean): Unit

  /**
    * Called after init() when this Screen should be displayed.
    *
    * @return path change for this Screen if there is an explicit path. Will only apply if the path is different or if
    *         force is set to true.
    */
  protected def activate(): Option[PathChange]

  /**
    * Deactivates the screen. Guaranteed to only be called after init and activate have been called. Called immediately
    * before the new screen is activated.
    */
  protected def deactivate(): Unit
}

/**
  * PathChange represents a path change request that returns from a ClientScreen.activate.
  *
  * @param path the new path to set.
  * @param replace replaces the current path in the browser history if true or pushes a new state if false. Defaults to
  *                false.
  * @param force forces the state change even if the path is the same as the current path. Defaults to false.
  */
case class PathChange(path: String, replace: Boolean = false, force: Boolean = false)

sealed trait StateChange

object StateChange {
  case object Initialized extends StateChange
  case object Activated extends StateChange
  case object Deactivated extends StateChange
}