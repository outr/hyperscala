package example

import java.io.File

import org.hyperscala.stream.{ById, ByTag, Delta, Selector}
import org.hyperscala.{PartialSupport, Request, ServerConnection, ServerScreen}

trait ServerDashboardScreen extends DashboardScreen with ServerScreen with PartialSupport with UserRequestValidator {
  override def template: File = new File("src/main/web/dashboard.html")

  override def partialParentId: String = "content"

  override def partialSelector: Selector = ById("example")

  override def deltas(request: Request): List[Delta] = List(
    Delta.ReplaceContent(ByTag("title"), "Modified Example Title")
  )

  override def activate(connection: ServerConnection): Unit = {
    logger.info(s"DashboardScreen activated!")
  }

  override def deactivate(connection: ServerConnection): Unit = {
    logger.info(s"DashboardScreen deactivated!")
  }
}
