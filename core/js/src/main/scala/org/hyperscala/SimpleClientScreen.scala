package org.hyperscala

import org.scalajs.dom.raw.HTMLElement

trait SimpleClientScreen[Main <: HTMLElement] extends ClientScreen {
  def main: Main

  override def activate(): Unit = main.style.display = "block"
  override def deactivate(): Unit = main.style.display = "none"
}
