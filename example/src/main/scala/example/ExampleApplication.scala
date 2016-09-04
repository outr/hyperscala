package example

import org.hyperscala.{Screen, SimpleScreen, WebApplication}
import pl.metastack.metarx.Channel

object ExampleApplication extends WebApplication {
  val login = create[LoginScreen]
  val dashboard = create[DashboardScreen]
  val profile = create[ProfileScreen]
  val error = server[ErrorScreen]
}

trait LoginScreen extends SimpleScreen {
  val authenticate: Channel[Authentication] = register[Authentication]
  val response: Channel[AuthResponse] = register[AuthResponse]

  override val path: String = "/login.html"
}

trait DashboardScreen extends SimpleScreen {
  override val path: String = "/dashboard.html"
}

trait ProfileScreen extends SimpleScreen {
  override def path: String = "/profile.html"
}

trait ErrorScreen extends Screen {
  override def isPathMatch(path: String): Boolean = false
}

case class Authentication(username: String, password: String)

case class AuthResponse(errorMessage: Option[String])