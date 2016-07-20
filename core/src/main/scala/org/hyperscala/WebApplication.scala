package org.hyperscala

import pl.metastack.metarx.Sub

abstract class WebApplication(host: String, port: Int) {
  protected var picklers = Vector.empty[Pickler[_]]
  protected var screens = Vector.empty[Screen]

  def create[S <: Screen]: S = None.orNull.asInstanceOf[S]      // Use Macros

  protected[hyperscala] def add[T](pickler: Pickler[T]): Unit = synchronized {
    val position = picklers.length
    picklers = picklers :+ pickler
    pickler.channel.attach { t =>
      if (!pickler.receiving.get()) {
        val json = pickler.write(t)
        send(position, json)
      }
    }
  }

  /**
    * Implement to support sending of JSON
    *
    * @param id the id of the pickler used
    * @param json the JSON data to send
    */
  // TODO: Macro for sending
  protected[hyperscala] def send(id: Int, json: String): Unit

  /**
    * Called to receive JSON and call the appropriate pickler.
    *
    * @param id the id of the pickler
    * @param json the JSON to unpickle
    */
  protected[hyperscala] def receive(id: Int, json: String): Unit = {
    val pickler = picklers(id)
    pickler.receive(json)
  }
}