package example

import java.io.File

import org.hyperscala.delta.{Delta, Selector}
import org.hyperscala.manager.ServerConnection
import org.hyperscala.{PartialSupport, Request, ServerScreen}

trait ServerDashboardScreen extends DashboardScreen with ServerScreen with PartialSupport with UserRequestValidator {
  override def template: File = new File("src/main/web/dashboard.html")

  override def partialParentId: String = "content"

  override def partialSelector: Selector = Selector.ById("example")

  override def deltas(request: Request): List[Delta] = {


    List(
      Delta.ReplaceContent(Selector.ByTag("title"), "Modified Example Title"),
      Delta.ReplaceContent(Selector.ById("list"), "")
      //    Delta.Repeat(ByClass("item"), data, (d: ItemData) => List(
      //      Delta.ReplaceContent(ByClass("itemName"), d.name),
      //      Delta.ReplaceContent(ByClass("itemValue"), d.value)
      //    ))
    )
  }

  override def activate(connection: ServerConnection): Unit = {
    logger.info(s"DashboardScreen activated!")
  }

  override def deactivate(connection: ServerConnection): Unit = {
    logger.info(s"DashboardScreen deactivated!")
  }
}
