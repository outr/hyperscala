package example

import java.io.File

import org.hyperscala._

object ExampleServer {
  val server = Server(ExampleApplication, "0.0.0.0", 8080)

  def session: ExampleSession = ExampleSession()

  def main(args: Array[String]): Unit = {
    server.resourceManager.mappings.classPath("html")()
    server.resourceManager.mappings.file(new File("src/main/web/"))()
    server.resourceManager.mappings.file(new File("src/main/web/")) { url =>
      if (url.path == "/download.txt") {
        Some(FileResourceInfo("/test.txt", "mydownload.txt"))
      } else {
        None
      }
    }
    server.errorHandler = ExampleApplication.error
    server.start()
  }
}