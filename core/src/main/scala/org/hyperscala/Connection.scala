package org.hyperscala

import pl.metastack.metarx.Sub

trait Connection {
  protected[hyperscala] var replace = false

  def app: WebApplication

  def initialURL: URL
  val url: Sub[URL] = Sub(initialURL)
//  val path: Sub[String] = Sub(url.map(_.path))
//  val params: Sub[Map[String, Param]] = Sub(url.map(_.parameters))
  val screen: Sub[BaseScreen] = Sub(app.byURL(initialURL))

  def init(): Unit

  def replaceWith(path: String): Unit = {
    replace = true
    try {
      url := url.get.copy(path = path)
    } finally {
      replace = false
    }
  }

  def replaceWith(screen: BaseScreen): Unit = {
    replace = true
    try {
      this.screen := screen
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