package org.hyperscala

import com.outr.scribe.Logging
import io.undertow.server.session.{SessionAttachmentHandler, SessionConfig, SessionCookieConfig, Session => UndertowSession}
import io.undertow.server.{DefaultResponseListener, HttpHandler, HttpServerExchange}
import io.undertow.util.{Headers, Sessions, StatusCodes}
import io.undertow.websockets.WebSocketConnectionCallback
import io.undertow.{Handlers, Undertow}

import scala.language.experimental.macros
import scala.language.implicitConversions

class Server(host: String, port: Int, sessionDomain: Option[String] = None) extends Logging {
  private val handler = new ServerHandler
  private var instance: Option[Undertow] = None
  private val mappedPathHandler = new MappedPathHandler
  private val sessionManager = new io.undertow.server.session.InMemorySessionManager("ServerSessionManager")
  private val sessionConfig = new SessionCookieConfig
  private val sessionAttachmentHandler = new SessionAttachmentHandler(sessionManager, sessionConfig) {
    setNext(handler)
  }
  val resourceManager = new FunctionalResourceManager(this)

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
    defaultHandler = Some(HttpPathHandler(new FunctionalResourceHandler(resourceManager)))
//    if (resourceManagers.nonEmpty) {
//      defaultHandler = Some(HttpPathHandler(Handlers.resource(new MultiResourceManager(resourceManagers.reverse: _*))))
//
//    }
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
    private val sessionConfig = new SessionCookieConfig {
      sessionDomain.foreach(setDomain)
    }

    override def handleRequest(exchange: HttpServerExchange): Unit = {
      exchange.putAttachment(SessionConfig.ATTACHMENT_KEY, sessionConfig)
      Server.withServerSession(Sessions.getOrCreateSession(exchange)) {
        exchange.addDefaultResponseListener(new DefaultResponseListener {
          override def handleDefaultResponse(exchange: HttpServerExchange): Boolean = if (exchange.getStatusCode >= 400) {
            errorSupport(errorHandler.handleRequest(exchange))
            true
          } else {
            false
          }
        })
        errorSupport {
          val handler = mappedPathHandler.lookup(exchange.url).orElse(defaultHandler)
          logger.debug(s"Looking up path: ${exchange.getRequestPath}, handler: $handler")
          handler.foreach(_.handleRequest(exchange))
        }
      }
    }
  }

  def error(t: Throwable): Unit = logger.error(t)
  def errorSupport[R](f: => R): R = try {
    f
  } catch {
    case t: Throwable => {
      error(t)
      throw t
    }
  }
}

object Server extends Logging {
  object request extends ThreadLocalStore
  object session extends Store {
    def undertowSession: UndertowSession = request[UndertowSession]("serverSession")
    override def apply[T](key: String): T = get[T](key).getOrElse(throw new NullPointerException(s"$key is not defined in the session."))
    override def get[T](key: String): Option[T] = Option(undertowSession.getAttribute(key).asInstanceOf[T])
    override def update[T](key: String, value: T): Unit = undertowSession.setAttribute(key, value)
    override def remove(key: String): Unit = undertowSession.removeAttribute(key)
  }

  private var wrapper: (() => Unit) => Unit = (f: () => Unit) => f()

  def wrap(wrapper: (() => Unit) => Unit): Unit = this.wrapper = wrapper

  def withServerSession[R](session: UndertowSession)(f: => R): R = request.scoped(Map.empty) {
    val previous = request.get("serverSession")
    request("serverSession") = session
    try {
      var result: Option[R] = None
      wrapper(() => {
        result = Some(f)
      })
      result.get
    } finally {
      previous match {
        case Some(s) => request("serverSession") = s
        case None =>
      }
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

  def apply(app: WebApplication, host: String, port: Int, sessionDomain: Option[String] = None): Server = {
    // Instantiate Server
    val server = new Server(host, port, sessionDomain) {
      override def error(t: Throwable): Unit = app.error(t)
    }

    // Create WebSocket communication handler
    val webSocketCallback = app.appManager.asInstanceOf[WebSocketConnectionCallback]
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
  def isURLMatch(url: URL): Boolean
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

  def lookup(url: URL): Option[PathHandler] = {
    explicitHandlers.find(_.isURLMatch(url)).orElse(paths.get(url.path).orElse(lookup(url, url.path.split("[/]").toList)))
  }

  def lookup(url: URL, path: List[String]): Option[PathHandler] = if (path.isEmpty) {
    explicitHandlers.find(_.isURLMatch(url))
  } else {
    explicitHandlers.find(_.isURLMatch(url))
      .orElse(lookupRecursive(url, path.head, path.tail))
      .orElse(lookupRecursive(url, "*", path.tail))
      .orElse(lookupRecursive(url, "**", path.tail))
  }

  private def lookupRecursive(url: URL, head: String, tail: List[String]): Option[PathHandler] = paths.get(head) match {
    case None => None
    case Some(handler) => handler match {
      case h: MappedPathHandler => h.lookup(url, tail)
      case _ if tail.isEmpty => Some(handler)
      case _ => None
    }
  }
}