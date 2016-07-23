package example

import com.outr.scribe.Logging
import org.hyperscala.{ClientScreen, WebApplication}
import org.scalajs.dom._

trait ClientLoginScreen extends LoginScreen with Logging with ClientScreen {
  // Configure form submit
  // TODO: load 'form' if not already loaded
  lazy val form = byId[html.Form]("login")
  lazy val message = byId[html.Div]("message")
  lazy val username = byId[html.Input]("username")
  lazy val password = byId[html.Input]("password")

  override def init(): Unit = {
    logger.info(s"Login init!")

    // Change screen upon successful login
    response.attach { r =>
      r.errorMessage match {
        case Some(msg) => message.innerHTML = msg
        case None => {
          message.innerHTML = ""
          app.connection.screen := Some(ExampleApplication.dashboard)
        }
      }
    }

    // Send authentication request to server
    form.onsubmit = (evt: Event) => {
      authenticate := Authentication(username.value, password.value)
      logger.info(s"Sent: ${username.value} / ${password.value}")
      false
    }
  }

  override def url: URL = "/login.html"

  override def activate(): Unit = {
    logger.info(s"Login Activated!")

    form.style.display = "block"
  }

  override def deactivate(): Unit = {
    logger.info(s"Login Deactivated!")

    form.style.display = "none"
  }
}
