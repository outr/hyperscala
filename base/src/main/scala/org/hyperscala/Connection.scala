package org.hyperscala

import pl.metastack.metarx.Sub

trait Connection {
  private[hyperscala] var replace = false

  def app: BaseApplication

  def init(): Unit

  val path: Sub[Option[String]] = Sub(None)
  val screen: Sub[Option[BaseScreen]] = Sub(None)

  def replaceWith(screen: BaseScreen): Unit = {
    replace = true
    try {
      this.screen := Some(screen)
    } finally {
      replace = false
    }
  }

  /**
    * Implement to support sending of JSON
    *
    * @param id the id of the pickler used
    * @param json the JSON data to send
    */
  def send(id: Int, json: String): Unit

  /**
    * Called to receive JSON and call the appropriate pickler.
    *
    * @param id the id of the pickler
    * @param json the JSON to unpickle
    */
  def receive(id: Int, json: String): Unit = {
    val pickler = app.picklers(id)
    pickler.receive(json)
  }
}