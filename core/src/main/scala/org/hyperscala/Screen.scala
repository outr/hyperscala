package org.hyperscala

import com.outr.scribe.Logging

trait Screen extends BaseScreen with Logging {
  def screenName: String

  /**
    * Defines the sorting order for the collection of Screens in the WebApplication. This is useful for fall-through
    * matching for multiple screens that may match the same path.
    *
    * Defaults to Screen.Priority.Normal
    */
  def priority: Int = Screen.Priority.Normal

  def app: WebApplication

  def isPathMatch(path: String): Boolean

  app.add(this)
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