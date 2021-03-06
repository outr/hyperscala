package example

import com.outr.reactify.Channel
import org.hyperscala.{Screen, SimpleScreen, SiteType, URL, WebApplication}

object ExampleApplication extends WebApplication(SiteType.SinglePage) {
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
  override def isURLMatch(url: URL): Boolean = false
}

case class Authentication(username: String, password: String)

case class AuthResponse(errorMessage: Option[String])