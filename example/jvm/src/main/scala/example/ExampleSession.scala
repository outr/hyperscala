package example

import org.hyperscala.Session
import pl.metastack.metarx.Sub

class ExampleSession extends Session {
  val username: Sub[Option[String]] = Sub[Option[String]](None)
}
