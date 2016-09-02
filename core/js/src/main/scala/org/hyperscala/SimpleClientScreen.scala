package org.hyperscala

import org.scalajs.dom.raw.HTMLElement

trait SimpleClientScreen[Main <: HTMLElement] extends ClientScreen with SimpleScreen {
  def main: Main

  protected def isReplace: Boolean = false

  override protected def activate(): Option[PathChange] = {
    logger.info(s"activate $toString")
    main.style.display = "block"
    Some(PathChange(path, isReplace))
  }

  override protected def deactivate(): Unit = {
    logger.info(s"deactivate $toString")
    main.style.display = "none"
  }
}
