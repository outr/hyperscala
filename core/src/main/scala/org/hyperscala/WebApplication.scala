package org.hyperscala

import pl.metastack.metarx.{StateChannel, Sub}

import scala.language.experimental.macros

abstract class WebApplication(val host: String, val port: Int) {
  protected[hyperscala] var picklers = Vector.empty[Pickler[_]]
  protected[hyperscala] var screens = Vector.empty[Screen]

  protected def createConnectionManager(): ConnectionManager = macro Macros.connectionManager
  protected val connectionManager: ConnectionManager

  def create[S <: Screen]: S = macro Macros.screen[S]
  def communicationPath: String = "/communication"

  protected[hyperscala] def add[T](pickler: Pickler[T]): Unit = synchronized {
    val position = picklers.length
    picklers = picklers :+ pickler
    pickler.channel.attach { t =>
      if (!pickler.receiving.get()) {
        val json = pickler.write(t)
        connectionManager.connection.send(position, json)
      }
    }
  }
}