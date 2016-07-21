package example

import org.hyperscala.{ConnectionManager, Screen, WebApplication}
import pl.metastack.metarx.Channel

object ExampleApplication extends WebApplication("localhost", 8080) {
  override protected val connectionManager: ConnectionManager = createConnectionManager()

  val login = create[LoginScreen]
  val dashboard = create[DashboardScreen]
}

trait LoginScreen extends Screen {
  val authenticate: Channel[Authentication] = register[Authentication]
  val response: Channel[AuthResponse] = register[AuthResponse]
}

trait DashboardScreen extends Screen {
}

case class Authentication(username: String, password: String)

case class AuthResponse(errorMessage: Option[String])