package example

import java.nio.file.Paths

import io.undertow.Handlers.resource
import io.undertow.server.handlers.resource.PathResourceManager
import org.hyperscala.{HttpPathHandler, Server, ServerScreen}

object ExampleServer {
  val server = Server(ExampleApplication)

  def session: ExampleSession = Server.session[ExampleSession]

  def main(args: Array[String]): Unit = {
    server.defaultHandler = Some(HttpPathHandler(resource(new PathResourceManager(Paths.get("src/main/web/"), 100))))
    server.errorHandler = ExampleApplication.error.asInstanceOf[ServerScreen]
    server.start()
  }
}