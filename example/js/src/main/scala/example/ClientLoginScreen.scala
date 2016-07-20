package example

import com.outr.scribe.Logging
import org.hyperscala.ClientScreen
import org.scalajs.dom.{Event, html}

trait ClientLoginScreen extends LoginScreen with Logging with ClientScreen {
  // Change screen upon successful login
  response.attach { r =>
    r.errorMessage match {
      case Some(message) => logger.warn(s"Failed to authenticate: $message")
      case None => app.screen := Some(ExampleApplication.dashboard)
    }
  }

  // Configure form submit
  // TODO: load 'form' if not already loaded
  lazy val form = byId[html.Form]("form")
  lazy val username = byId[html.Input]("username")
  lazy val password = byId[html.Input]("password")

  override def init(): Unit = {
    logger.info(s"Login init!")

    // Send authentication request to server
    form.onsubmit = (evt: Event) => {
      authenticate := Authentication(username.value, password.value)
      false
    }
  }

  override def activate(): Unit = {
    logger.info(s"Login Activated!")

    form.style.display = "block"
  }

  override def deactivate(): Unit = {
    logger.info(s"Login Deactivated!")

    form.style.display = "none"
  }
}
