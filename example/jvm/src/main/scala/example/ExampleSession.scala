package example

import com.outr.reactify.Var
import org.hyperscala.{DefaultSessionManager, Session}

class ExampleSession extends Session {
  val username: Var[Option[String]] = Var[Option[String]](None)
}

object ExampleSession extends DefaultSessionManager[ExampleSession] {
  override protected def create(): ExampleSession = new ExampleSession
}