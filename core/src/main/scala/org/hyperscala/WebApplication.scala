package org.hyperscala

import pl.metastack.metarx.Sub

import scala.language.experimental.macros

abstract class WebApplication(host: String, port: Int) {
  protected[hyperscala] var picklers = Vector.empty[Pickler[_]]
  protected[hyperscala] var screens = Vector.empty[Screen]
  protected[hyperscala] var connections = Set.empty[Connection]
  protected[hyperscala] val currentConnection = new ThreadLocal[Option[Connection]] {
    override def initialValue(): Option[Connection] = None
  }
  protected[hyperscala] def connection: Connection = currentConnection.get().getOrElse(throw new RuntimeException(s"Connection not specified."))

  def create[S <: Screen]: S = macro Macros.screen[S]

  protected[hyperscala] def add[T](pickler: Pickler[T]): Unit = synchronized {
    val position = picklers.length
    picklers = picklers :+ pickler
    pickler.channel.attach { t =>
      if (!pickler.receiving.get()) {
        val json = pickler.write(t)
        connection.send(position, json)
      }
    }
  }
}