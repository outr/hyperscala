package example

import java.nio.file.Paths

import org.hyperscala._

object ExampleServer {
  val server = Server(ExampleApplication)

  def session: ExampleSession = Server.session[ExampleSession]

  def main(args: Array[String]): Unit = {
    server.pathResources(Paths.get("src/main/web/"))
    server.errorHandler = ExampleApplication.error
    server.start()
  }
}