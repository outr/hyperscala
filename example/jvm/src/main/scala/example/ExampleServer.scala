package example

import java.nio.file.Paths

import org.hyperscala._

object ExampleServer {
  val server = Server(ExampleApplication, "localhost", 8080)

  def session: ExampleSession = ExampleSession()

  def main(args: Array[String]): Unit = {
    server.pathResources(Paths.get("src/main/web/"))
    server.errorHandler = ExampleApplication.error
    server.start()
  }
}