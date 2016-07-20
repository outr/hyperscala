package example

import java.io.File

import io.undertow.server.HttpServerExchange
import org.hyperscala.ServerScreen
import org.hyperscala.stream.{ByTag, Delta}

trait ServerDashboardScreen extends DashboardScreen with ServerScreen {
  override def template: File = new File("src/main/web/example.html")

  override def deltas(exchange: HttpServerExchange): List[Delta] = List(
    Delta.ReplaceContent(ByTag("title"), "Modified Example Title")
  )
}
