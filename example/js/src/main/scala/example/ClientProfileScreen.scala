package example

import com.outr.scribe.Logging
import org.hyperscala._
import org.scalajs.dom._

import scala.scalajs.js.Date

trait ClientProfileScreen extends ProfileScreen with SimpleClientScreen[html.Div] with Logging {
  override def main = byId[html.Div]("profile")
  def dashboardButton = byId[html.Button]("dashboardButton")
  def reloadButton = byId[html.Button]("reloadButton")
  def createdBlock = byId[html.Element]("created")

  override def init(state: InitState): Unit = {
    logger.info(s"Profile init! (state: $state)")

    createdBlock.innerHTML = new Date().toTimeString()

    reloadButton.onclick = (evt: Event) => {
      logger.info("Requesting reload of page...")
      requestReloadContent()

      false
    }
    dashboardButton.onclick = (evt: Event) => {
      app.connection.screen := ExampleApplication.dashboard

      false
    }
  }
}