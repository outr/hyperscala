package example

import org.hyperscala.{Screen, WebApplication}
import pl.metastack.metarx.Channel

object ExampleApplication extends WebApplication("localhost", 8080) {
  val login = create[LoginScreen]
  val dashboard = create[DashboardScreen]

  override protected[hyperscala] def send(id: Int, json: String): Unit = {
    // TODO: implement
  }
}

trait LoginScreen extends Screen {
  val authenticate: Channel[Authentication] = register[Authentication]
  val response: Channel[AuthResponse] = register[AuthResponse]
}

trait DashboardScreen extends Screen {
}

case class Authentication(username: String, password: String)

case class AuthResponse(errorMessage: Option[String])