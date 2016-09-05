package org.hyperscala

import org.scalajs.dom.raw.HTMLElement

trait SimpleClientScreen[Main <: HTMLElement] extends ClientScreen with SimpleScreen {
  def main: Main

  protected def isReplace: Boolean = false

  override protected def activate(): Option[PathChange] = {
    logger.debug(s"activate $toString, $path")
    main.style.display = "block"
    Some(PathChange(path, isReplace))
  }

  override protected def deactivate(): Unit = {
    logger.debug(s"deactivate $toString")
    main.style.display = "none"
  }
}
