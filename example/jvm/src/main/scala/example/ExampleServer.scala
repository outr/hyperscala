package example

import java.io.File

import org.hyperscala._

object ExampleServer {
  val server = Server(ExampleApplication, "localhost", 8080)

  def session: ExampleSession = ExampleSession()

  def main(args: Array[String]): Unit = {
//    server.pathResources(Paths.get("src/main/web/"))
    server.resourceManager.fileMapping(new File("src/main/web/"), (path: String) => {
      println(s"Checking path: $path, Session: $session")
      Some(path)
    })
    server.errorHandler = ExampleApplication.error
    server.start()
  }
}