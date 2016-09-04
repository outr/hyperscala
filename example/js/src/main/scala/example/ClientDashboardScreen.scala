package example

import com.outr.scribe.Logging
import org.hyperscala._
import org.scalajs.dom._

trait ClientDashboardScreen extends DashboardScreen with SimpleClientScreen[html.Div] with Logging {
  def main = byId[html.Div]("example")
  def profileButton = byId[html.Button]("profileButton")

  override def init(isPage: Boolean): Unit = {
    logger.info(s"Dashboard init! (isPage: $isPage)")

    profileButton.onclick = (evt: Event) => {
      app.connection.screen := ExampleApplication.profile

      false
    }
  }
}