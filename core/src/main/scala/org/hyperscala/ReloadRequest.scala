package org.hyperscala

case class ReloadRequest(force: Boolean, url: Option[String] = None)