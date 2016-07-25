package org.hyperscala

trait SimpleScreen extends Screen {
  def path: String

  override final def isPathMatch(path: String): Boolean = path == this.path
}
