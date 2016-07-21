package org.hyperscala

import com.outr.scribe.Logging
import io.undertow.{Handlers, Undertow}
import io.undertow.server.{DefaultResponseListener, HttpHandler, HttpServerExchange}
import io.undertow.util.{Headers, StatusCodes}
import io.undertow.websockets.WebSocketConnectionCallback

import scala.language.implicitConversions

class Server(host: String, port: Int) extends Logging {
  private val handler = new ServerHandler
  private var instance: Option[Undertow] = None
  private val mappedPathHandler = new MappedPathHandler

  var defaultHandler: Option[PathHandler] = None
  var errorHandler: HttpHandler = new HttpHandler {
    override def handleRequest(exchange: HttpServerExchange): Unit = {
      val errorPage =
        s"""<html>
           |<head>
           |  <title>Error</title>
           |</head>
           |<body>
           |  ${exchange.getStatusCode} - ${StatusCodes.getReason(exchange.getStatusCode)}
           |</body>
           |</html>""".stripMargin
      exchange.getResponseHeaders.put(Headers.CONTENT_LENGTH, errorPage.length)
      exchange.getResponseHeaders.put(Headers.CONTENT_TYPE, "text/html")
      exchange.getResponseSender.send(errorPage)
    }
  }

  implicit def httpHandler2PathHandler(h: HttpHandler): PathHandler = HttpPathHandler(h)

  def start(): Unit = synchronized {
    val server = Undertow.builder()
      .addHttpListener(port, host)
      .setHandler(handler)
      .build()
    server.start()
    instance = Some(server)
    logger.info(s"Server started on $host:$port...")
  }

  def stop(): Unit = synchronized {
    instance match {
      case Some(server) => {
        server.stop()
        instance = None
        logger.info(s"Server stopped")
      }
      case None => // Not started
    }
  }

  def register(handler: PathHandler, paths: String*): Unit = {
    logger.info(s"Registering ${paths.mkString(", ")}")
    paths.foreach { path =>
      mappedPathHandler.register(path, handler)
    }
  }

  def register(explicitHandler: ExplicitHandler): Unit = mappedPathHandler.register(explicitHandler)

  def register(path: String, contentType: String, f: HttpServerExchange => String): Unit = {
    val handler = new HttpPathHandler {
      override def handleRequest(exchange: HttpServerExchange): Unit = {
        val content = f(exchange)
        exchange.getResponseHeaders.put(Headers.CONTENT_LENGTH, content.length)
        exchange.getResponseHeaders.put(Headers.CONTENT_TYPE, contentType)
        val sender = exchange.getResponseSender
        sender.send(content)
      }
    }
    register(handler, path)
  }

  class ServerHandler extends HttpHandler {
    override def handleRequest(exchange: HttpServerExchange): Unit = {
      exchange.addDefaultResponseListener(new DefaultResponseListener {
        override def handleDefaultResponse(exchange: HttpServerExchange): Boolean = if (exchange.getStatusCode >= 400) {
          errorHandler.handleRequest(exchange)
          true
        } else {
          false
        }
      })
      logger.info(s"Looking up path: ${exchange.getRequestPath}")
      mappedPathHandler.lookup(exchange.getRequestPath) match {
        case Some(h) => h.handleRequest(exchange)
        case None => defaultHandler match {
          case Some(h) => h.handleRequest(exchange)
          case None => // Nothing
        }
      }
    }
  }
}

object Server {
  def main(args: Array[String]): Unit = {
    val server = new Server("localhost", 8080)
    server.register("/", "text/html", (hse: HttpServerExchange) => {
      "<html><head><title>Root</title></head><body>This is the root</body></html>"
    })
    server.register("/test/**/wildcard.txt", "text/plain", (hse: HttpServerExchange) => "Wildcard!")
    server.start()
  }

  def apply(app: WebApplication): Server = {
    // Instantiate Server
    val server = new Server(app.host, app.port)

    // Create WebSocket communication handler
    val webSocketCallback = app.connectionManager.asInstanceOf[WebSocketConnectionCallback]
    server.register(HttpPathHandler(Handlers.websocket(webSocketCallback)), app.communicationPath)

    // Register screens
    app.screens.foreach {
      case screen: ServerScreen => server.register(screen)
    }

    server
  }
}

trait PathHandler {
  def handleRequest(exchange: HttpServerExchange): Unit
}

trait ExplicitHandler extends PathHandler {
  def isPathMatch(path: String): Boolean
}

trait HttpPathHandler extends HttpHandler with PathHandler

object HttpPathHandler {
  def apply(handler: HttpHandler): HttpPathHandler = new HttpPathHandler {
    override def handleRequest(exchange: HttpServerExchange): Unit = handler.handleRequest(exchange)
  }
}

class MappedPathHandler extends PathHandler {
  protected var explicitHandlers = Set.empty[ExplicitHandler]
  protected var paths = Map.empty[String, PathHandler]

  def register(explicitHandler: ExplicitHandler): Unit = synchronized {
    explicitHandlers += explicitHandler
  }

  def register(path: String, handler: PathHandler): Unit = synchronized {
    if (path.indexOf('*') != -1) {
      val parts = path.split("[/]").toList
      registerRecursive(parts, handler)
    } else {
      paths += path -> handler
    }
  }

  protected def registerRecursive(parts: List[String], handler: PathHandler): Unit = {
    val head = parts.head
    if (parts.tail.isEmpty) {
      handler match {
        case explicit: ExplicitHandler => explicitHandlers += explicit
        case _ => paths += head -> handler
      }
    } else if (head == "**") {
      paths += head -> this       // ** recurses back on itself
      registerRecursive(parts.tail, handler)
    } else {
      val mappedHandler = paths.getOrElse(head, new MappedPathHandler).asInstanceOf[MappedPathHandler]
      paths += head -> mappedHandler
      mappedHandler.registerRecursive(parts.tail, handler)
    }
  }

  def handleRequest(exchange: HttpServerExchange): Unit = {
    throw new RuntimeException("MappedPathHandler is not a valid request handler")
  }

  def lookup(path: String): Option[PathHandler] = {
    explicitHandlers.find(_.isPathMatch(path)).orElse(
      paths.get(path).orElse(lookup(path, path.split("[/]").toList))
    )
  }

  def lookup(fullPath: String, path: List[String]): Option[PathHandler] = if (path.isEmpty) {
    explicitHandlers.find(_.isPathMatch(fullPath))
  } else {
    explicitHandlers.find(_.isPathMatch(fullPath))
      .orElse(lookupRecursive(fullPath, path.head, path.tail))
      .orElse(lookupRecursive(fullPath, "*", path.tail))
      .orElse(lookupRecursive(fullPath, "**", path.tail))
  }

  private def lookupRecursive(fullPath: String, head: String, tail: List[String]): Option[PathHandler] = paths.get(head) match {
    case None => None
    case Some(handler) => handler match {
      case h: MappedPathHandler => h.lookup(fullPath, tail)
      case _ if tail.isEmpty => Some(handler)
      case _ => None
    }
  }
}