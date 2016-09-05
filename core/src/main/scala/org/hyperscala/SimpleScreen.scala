package org.hyperscala

trait SimpleScreen extends Screen {
  def path: String

  override final def isURLMatch(url: URL): Boolean = url.path == path
}
