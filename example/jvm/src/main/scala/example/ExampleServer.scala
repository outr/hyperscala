package example

import java.io.File

import org.hyperscala._

object ExampleServer {
  val server = Server(ExampleApplication, "localhost", 8080)

  def session: ExampleSession = ExampleSession()

  def main(args: Array[String]): Unit = {
    server.resourceManager.classPathMapping("html")()
    server.resourceManager.fileMapping(new File("src/main/web/"))()
    server.errorHandler = ExampleApplication.error
    server.start()
  }
}