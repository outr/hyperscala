package example

import java.io.File

import org.hyperscala.manager.ServerConnection
import org.hyperscala.stream.{ById, ByTag, Delta, Selector}
import org.hyperscala.{PartialSupport, Request, ServerScreen}

trait ServerProfileScreen extends ProfileScreen with ServerScreen with PartialSupport with UserRequestValidator {
  override def template: File = new File("src/main/web/profile.html")

  override def partialParentId: String = "content"

  override def partialSelector: Selector = ById("profile")

  override def deltas(request: Request): List[Delta] = Nil

  override def activate(connection: ServerConnection): Unit = {
    logger.info(s"ProfileScreen activated!")
  }

  override def deactivate(connection: ServerConnection): Unit = {
    logger.info(s"ProfileScreen deactivated!")
  }
}
