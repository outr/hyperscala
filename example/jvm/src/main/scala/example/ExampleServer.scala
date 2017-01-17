package example

import java.io.File

import org.hyperscala._

object ExampleServer {
  val server = Server(ExampleApplication)
  server.config.host := "0.0.0.0"
  server.config.port := 8080

  def session: ExampleSession = ExampleSession()

  def main(args: Array[String]): Unit = {
    server.resourceManager.classPath("html")()
    server.resourceManager.file(new File("src/main/web/")) { url =>
      if (url.path.endsWith(".html")) {
        None
      } else {
        val file = new File("src/main/web/", url.path)
        if (file.exists()) {
          Some(FileResourceInfo(file))
        } else {
          None
        }
      }
    }
    server.resourceManager.file(new File("src/main/web/")) { url =>
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