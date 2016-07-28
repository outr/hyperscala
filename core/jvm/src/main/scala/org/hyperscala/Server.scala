package org.hyperscala

import java.nio.file.Path

import com.outr.scribe.{Logging, Platform}
import io.undertow.server.handlers.resource.{ClassPathResourceManager, PathResourceManager, ResourceManager}
import io.undertow.server.session.{InMemorySessionManager, SessionAttachmentHandler, SessionCookieConfig, Session => UndertowSession}
import io.undertow.server.{DefaultResponseListener, HttpHandler, HttpServerExchange}
import io.undertow.util.{Headers, Sessions, StatusCodes}
import io.undertow.websockets.WebSocketConnectionCallback
import io.undertow.{Handlers, Undertow}

import scala.language.experimental.macros
import scala.language.implicitConversions

class Server(host: String, port: Int) extends Logging {
  private val handler = new ServerHandler
  private var instance: Option[Undertow] = None
  private val mappedPathHandler = new MappedPathHandler
  private val sessionManager = new InMemorySessionManager("ServerSessionManager")
  private val sessionConfig = new SessionCookieConfig
  private val sessionAttachmentHandler = new SessionAttachmentHandler(sessionManager, sessionConfig) {
    setNext(handler)
  }
  private var resourceManagers = List.empty[ResourceManager]
  def classpathResources(classLoader: ClassLoader = Thread.currentThread().getContextClassLoader, prefix: String = ""): Unit = synchronized {
    resourceManagers = new ClassPathResourceManager(classLoader, prefix) :: resourceManagers
  }
  def pathResources(path: Path, transferMinSize: Long = 100L): Unit = synchronized {
    resourceManagers = new PathResourceManager(path, transferMinSize) :: resourceManagers
  }

  private var defaultHandler: Option[PathHandler] = None
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

  def start(): Unit = synchronized {
    if (resourceManagers.nonEmpty) {
      defaultHandler = Some(HttpPathHandler(Handlers.resource(new MultiResourceManager(resourceManagers.reverse: _*))))
    }
    val server = Undertow.builder()
      .addHttpListener(port, host)
      .setHandler(sessionAttachmentHandler)
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
      Server._session.set(Option(Sessions.getOrCreateSession(exchange)))
      try {
        exchange.addDefaultResponseListener(new DefaultResponseListener {
          override def handleDefaultResponse(exchange: HttpServerExchange): Boolean = if (exchange.getStatusCode >= 400) {
            errorSupport(errorHandler.handleRequest(exchange))
            true
          } else {
            false
          }
        })
        errorSupport {
          val handler = mappedPathHandler.lookup(exchange.getRequestPath).orElse(defaultHandler)
          logger.debug(s"Looking up path: ${exchange.getRequestPath}, handler: $handler")
          handler.foreach(_.handleRequest(exchange))
        }
      } finally {
        Server._session.remove()
      }
    }
  }

  protected def error(t: Throwable): Unit = logger.error(Platform.throwable2String(t))
  protected def errorSupport[R](f: => R): R = try {
    f
  } catch {
    case t: Throwable => {
      error(t)
      throw t
    }
  }
}

object Server extends Logging {
  private val _session = new ThreadLocal[Option[UndertowSession]] {
    override def initialValue(): Option[UndertowSession] = None
  }
  def serverSession: Option[UndertowSession] = _session.get()
  def session[S <: Session]: S = macro Session.session[S]

  def withServerSession[R](session: UndertowSession)(f: => R): R = {
    _session.set(Some(session))
    try {
      f
    } finally {
      _session.remove()
    }
  }

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
    val webSocketCallback = app.manager.asInstanceOf[WebSocketConnectionCallback]
    server.register(HttpPathHandler(Handlers.websocket(webSocketCallback)), app.communicationPath)

    // Register screens
    app.screens.foreach {
      case screen: ServerScreen => server.register(screen)
    }

    // Initialize WebApplication
    app.init()

    server
  }
}

trait PathHandler extends HttpHandler {
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