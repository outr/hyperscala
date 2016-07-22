package org.hyperscala

import pl.metastack.metarx.{StateChannel, Sub}

import scala.language.experimental.macros

abstract class WebApplication(val host: String, val port: Int) extends BaseApplication {
  override protected[hyperscala] var picklers = Vector.empty[Pickler[_]]
  override protected[hyperscala] var _screens = Vector.empty[Screen]
  def screens: Vector[Screen] = _screens

  val manager: ApplicationManager = createApplicationManager()

  def create[S <: Screen]: S = macro Macros.screen[S]
  def communicationPath: String = "/communication"

  protected[hyperscala] def add[T](pickler: Pickler[T]): Unit = synchronized {
    val position = picklers.length
    picklers = picklers :+ pickler
    pickler.channel.attach { t =>
      if (!pickler.receiving.get()) {
        val json = pickler.write(t)
        manager.connection.send(position, json)
      }
    }
  }
}