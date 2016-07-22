package example

import com.outr.scribe.Logging
import org.hyperscala.ClientScreen
import org.scalajs.dom._

trait ClientDashboardScreen extends DashboardScreen with ClientScreen with Logging {
  override def init(): Unit = {
    logger.info("Dashboard init!")
  }

  override def activate(): URL = {
    logger.info(s"Dashboard activate!")
    "/dashboard.html"
  }

  override def deactivate(): Unit = {
    logger.info("Dashboard deactivate!")
  }
}
