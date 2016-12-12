package org.hyperscala

import com.outr.scribe.Logging

trait Screen extends BaseScreen with Logging {
  def screenName: String
  def shortScreenName: String = {
    val s = screenName
    val lastIndex = s.lastIndexOf('.')
    if (lastIndex != -1) {
      s.substring(lastIndex + 1)
    } else {
      s
    }
  }

  /**
    * Defines the sorting order for the collection of Screens in the WebApplication. This is useful for fall-through
    * matching for multiple screens that may match the same path.
    *
    * Defaults to Screen.Priority.Normal
    */
  def priority: Priority = Priority.Normal

  def app: WebApplication

  def isURLMatch(url: URL): Boolean

  def isCurrentScreen: Boolean = app.connection.screen.get == this

  app.add(this)
  app.screensByName += screenName -> this
}

object Priority {
  private var nameMap = Map.empty[String, Priority]

  val Lowest = Priority(0, "lowest")
  val Low = Priority(100, "low")
  val Normal = Priority(1000, "normal")
  val High = Priority(10000, "high")
  val Critical = Priority(10000, "critical")

  def get(name: String): Option[Priority] = nameMap.get(name)
}

case class Priority(value: Int, name: String) extends Ordered[Priority] {
  Priority.synchronized {
    Priority.nameMap += name -> this
  }

  def lower(name: String): Priority = Priority(value - 1, name)
  def higher(name: String): Priority = Priority(value + 1, name)

  override def compare(that: Priority): Int = that.value.compareTo(value)
}