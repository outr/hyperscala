package org.hyperscala

trait Screen extends BaseScreen {
  def screenName: String
  override def priority: Int = Screen.Priority.Normal

  override def app: WebApplication

  app.screensByName += screenName -> this
}

object Screen {
  object Priority {
    val Critical = 1
    val High = 50
    val Normal = 100
    val Low = 200
  }
}