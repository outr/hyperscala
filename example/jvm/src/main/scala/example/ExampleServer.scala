package example

import org.hyperscala.Server

object ExampleServer {
  val server = Server(ExampleApplication)

  def main(args: Array[String]): Unit = {
    server.start()
  }
}
