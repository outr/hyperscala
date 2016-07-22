package example

import org.hyperscala.{Screen, WebApplication}
import pl.metastack.metarx.Channel

object ExampleApplication extends WebApplication("localhost", 8080) {
  val login = create[LoginScreen]
  val dashboard = create[DashboardScreen]
}

trait LoginScreen extends Screen {
  val authenticate: Channel[Authentication] = register[Authentication]
  val response: Channel[AuthResponse] = register[AuthResponse]

  override def isPathMatch(path: String): Boolean = path == "/login.html"
}

trait DashboardScreen extends Screen {
  override def isPathMatch(path: String): Boolean = path == "/dashboard.html"
}

case class Authentication(username: String, password: String)

case class AuthResponse(errorMessage: Option[String])