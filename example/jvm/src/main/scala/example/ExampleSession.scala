package example

import org.hyperscala.{DefaultSessionManager, Session}
import pl.metastack.metarx.Sub

class ExampleSession extends Session {
  val username: Sub[Option[String]] = Sub[Option[String]](None)
}

object ExampleSession extends DefaultSessionManager[ExampleSession] {
  override protected def create(): ExampleSession = new ExampleSession
}