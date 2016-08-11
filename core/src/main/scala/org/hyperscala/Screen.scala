package org.hyperscala

trait Screen extends BaseScreen {
  def screenName: String
  override def app: WebApplication

  app.screensByName += screenName -> this
}
