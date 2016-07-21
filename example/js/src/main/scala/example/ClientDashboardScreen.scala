package example

import com.outr.scribe.Logging
import org.hyperscala.ClientScreen
import org.scalajs.dom._

trait ClientDashboardScreen extends DashboardScreen with ClientScreen with Logging {
  override protected def init(): Unit = {
    logger.info("Dashboard init!")
  }

  override protected def activate(): URL = {
    logger.info(s"Dashboard activate!")
    "/dashboard.html"
  }

  override protected def deactivate(): Unit = {
    logger.info("Dashboard deactivate!")
  }

  override def isActive: Boolean = document.location.pathname == "/dashboard.html"
}
