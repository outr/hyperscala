package example

import org.hyperscala.{Screen, SimpleScreen, WebApplication}
import pl.metastack.metarx.Channel

object ExampleApplication extends WebApplication {
  val login = create[LoginScreen]
  val dashboard = create[DashboardScreen]
  val error = server[ErrorScreen]
}

trait LoginScreen extends SimpleScreen {
  val authenticate: Channel[Authentication] = register[Authentication]
  val response: Channel[AuthResponse] = register[AuthResponse]

  override def path: Some[String] = Some("/login.html")
}

trait DashboardScreen extends SimpleScreen {
  override def path: Some[String] = Some("/dashboard.html")
}

trait ErrorScreen extends Screen {
  override def isPathMatch(path: String): Boolean = false
}

case class Authentication(username: String, password: String)

case class AuthResponse(errorMessage: Option[String])