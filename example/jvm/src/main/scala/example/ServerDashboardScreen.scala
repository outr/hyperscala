package example

import java.io.File

import org.hyperscala.{Connection, ServerScreen}
import org.hyperscala.stream.{ById, ByTag, Delta, Selector}

trait ServerDashboardScreen extends DashboardScreen with ServerScreen {
  override def template: File = new File("src/main/web/example.html")

  override def partialParentId: String = "content"

  override def partialSelector: Selector = ById("example")

  override def deltas(): List[Delta] = List(
    Delta.ReplaceContent(ByTag("title"), "Modified Example Title")
  )

  override def activate(connection: Connection): Unit = {
    logger.info(s"DashboardScreen activated!")
  }

  override def deactivate(connection: Connection): Unit = {
    logger.info(s"DashboardScreen deactivated!")
  }
}
