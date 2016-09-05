package org.hyperscala

import org.scalajs.dom.raw.HTMLElement

trait SimpleClientScreen[Main <: HTMLElement] extends ClientScreen with SimpleScreen {
  def main: Main

  protected def isReplace: Boolean = false

  override protected def activate(url: URL): Option[URLChange] = {
    logger.debug(s"activate $toString, $path")
    main.style.display = "block"
    Some(URLChange(url.copy(path = path), isReplace))
  }

  override protected def deactivate(): Unit = {
    logger.debug(s"deactivate $toString")
    main.style.display = "none"
  }
}
