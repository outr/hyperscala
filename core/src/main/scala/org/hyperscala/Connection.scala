package org.hyperscala

import com.outr.reactify.Var
import com.outr.scribe.Logging

trait Connection extends Logging {
  protected[hyperscala] var replace = false

  def app: WebApplication

  def initialURL: URL
  lazy val url: Var[URL] = Var[URL](initialURL)
  lazy val screen: Var[BaseScreen] = Var[BaseScreen](app.byURL(initialURL))

  def init(): Unit = {
    logger.info(s"Initial URL: ${url.get}, Starting Screen: ${screen.get}")
  }

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