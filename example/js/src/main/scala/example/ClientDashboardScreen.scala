package example

import com.outr.scribe.Logging
import org.hyperscala.ClientScreen
import org.scalajs.dom._

trait ClientDashboardScreen extends DashboardScreen with ClientScreen with Logging {
  def example = byId[html.Div]("example")

  override def init(): Unit = {
    logger.info("Dashboard init!")
  }

  override def path: Path = "/dashboard.html"

  override def activate(): Unit = {
    logger.info(s"Dashboard activate!")

    example.style.display = "block"
  }

  override def deactivate(): Unit = {
    logger.info("Dashboard deactivate!")

    example.style.display = "none"
  }
}
