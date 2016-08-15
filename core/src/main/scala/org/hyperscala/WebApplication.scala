package org.hyperscala

import pl.metastack.metarx.Channel

import scala.language.experimental.macros

abstract class WebApplication(val host: String, val port: Int) extends BaseApplication {
  override protected[hyperscala] var picklers = Vector.empty[Pickler[_]]
  override protected[hyperscala] var _screens = Vector.empty[BaseScreen]
  private[hyperscala] var screensByName = Map.empty[String, Screen]

  lazy val manager: ApplicationManager = createApplicationManager()

  val pathChanged: Channel[PathChanged] = register[PathChanged]
  val screenContentRequest: Channel[ScreenContentRequest] = register[ScreenContentRequest]
  val screenContentResponse: Channel[ScreenContentResponse] = register[ScreenContentResponse]

  def screens: Vector[Screen] = _screens.asInstanceOf[Vector[Screen]]

  def connection: Connection = manager.connection

  def create[S <: Screen]: S = macro Macros.screen[S]
  def server[S <: Screen]: S = macro Macros.serverScreen[S]
  def communicationPath: String = "/communication"

  protected[hyperscala] def add[T](pickler: Pickler[T]): Unit = synchronized {
    val position = picklers.length
    picklers = picklers :+ pickler
    pickler.channel.attach { t =>
      if (!pickler.isReceiving(t)) {
        val json = pickler.write(t)
        manager.connection.send(position, json)
      }
    }
  }

  def init(): Unit = manager.init()

  def byName(screenName: String): Option[Screen] = screensByName.get(screenName)
}