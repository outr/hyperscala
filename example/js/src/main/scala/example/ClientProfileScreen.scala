package example

import com.outr.scribe.Logging
import org.hyperscala._
import org.scalajs.dom._

trait ClientProfileScreen extends ProfileScreen with SimpleClientScreen[html.Div] with Logging {
  override def main = byId[html.Div]("profile")
  def dashboardButton = byId[html.Button]("dashboardButton")

  override def init(isPage: Boolean): Unit = {
    logger.info(s"Profile init! (isPage: $isPage)")

    dashboardButton.onclick = (evt: Event) => {
      app.connection.screen := ExampleApplication.dashboard

      false
    }
  }
}