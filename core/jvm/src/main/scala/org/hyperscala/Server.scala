package org.hyperscala

import com.outr.scribe.Logging
import io.undertow.server.session.{SessionAttachmentHandler, SessionConfig, SessionCookieConfig, Session => UndertowSession}
import io.undertow.server.{DefaultResponseListener, HttpHandler, HttpServerExchange}
import io.undertow.util.{Headers, Sessions, StatusCodes}
import io.undertow.websockets.WebSocketConnectionCallback
import io.undertow.{Handlers, Undertow}

import scala.collection.immutable.SortedSet
import scala.language.experimental.macros
import scala.language.implicitConversions
import scala.util.matching.Regex

class Server(host: String, port: Int, sessionDomain: Option[String] = None) extends Logging {
  private val handler = new ServerHandler
  private var instance: Option[Undertow] = None
  private var handlers = List.empty[Handler]
  private val sessionManager = new io.undertow.server.session.InMemorySessionManager("ServerSessionManager")
  private val sessionConfig = new SessionCookieConfig
  private val sessionAttachmentHandler = new SessionAttachmentHandler(sessionManager, sessionConfig) {
    setNext(handler)
  }
  val resourceManager = new FunctionalResourceManager(this)
  private val resourceHandler = new FunctionalResourceHandler(resourceManager)
  register(resourceHandler)

//  private var defaultHandler: Option[PathHandler] = None
  var errorHandler: Handler = new Handler {
    override def isURLMatch(url: URL): Boolean = false

    override def priority: Priority = Priority.Normal

    override def handleRequest(url: URL, exchange: HttpServerExchange): Unit = {
      logger.info("Error Handler!")
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

  def register(handler: Handler): Unit = synchronized {
    handlers = (handler :: handlers).sorted
  }

  def register(handler: HttpHandler, paths: String*): Unit = {
    logger.info(s"Registering ${paths.mkString(", ")}")
    register(Handler.path(paths.toSet, handler))
  }

  def register(path: String, contentType: String, f: HttpServerExchange => String): Unit = {
    val handler = new Handler {
      def isURLMatch(url: URL): Boolean = url.path == path

      override def handleRequest(url: URL, exchange: HttpServerExchange): Unit = {
        val content = f(exchange)
        exchange.getResponseHeaders.put(Headers.CONTENT_LENGTH, content.length)
        exchange.getResponseHeaders.put(Headers.CONTENT_TYPE, contentType)
        val sender = exchange.getResponseSender
        sender.send(content)
      }

      override def priority: Priority = Priority.Normal
    }
    register(handler)
  }

  class ServerHandler extends HttpHandler {
    private val sessionConfig = new SessionCookieConfig {
      sessionDomain.foreach(setDomain)
    }

    override def handleRequest(exchange: HttpServerExchange): Unit = {
      exchange.putAttachment(SessionConfig.ATTACHMENT_KEY, sessionConfig)
      Server.withServerSession(Sessions.getOrCreateSession(exchange)) {
        errorSupport {
          val url = exchange.url
          val handler = handlers.find(h => h.isURLMatch(url))
          handler match {
            case Some(h) => h.handleRequest(url, exchange)
            case None => {
              exchange.setStatusCode(404)
              errorHandler.handleRequest(url, exchange)
            }
          }
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
    server.register(Handlers.websocket(webSocketCallback), app.communicationPath)

    // Register screens
    app.screens.foreach {
      case screen: ServerScreen => server.register(screen)
    }

    // Initialize WebApplication
    app.init()

    server
  }
}

trait Handler extends Ordered[Handler] {
  /**
    * Returns true if this URL should be handled by this Handler. The handleRequest method will be invoked following
    * a true response.
    *
    * @param url the current URL being handled
    * @return true if this handler is equipped to handle it
    */
  def isURLMatch(url: URL): Boolean

  /**
    * Handles the exchange.
    *
    * @param url the current URL
    * @param exchange the HTTP request
    */
  def handleRequest(url: URL, exchange: HttpServerExchange): Unit

  /**
    * The priority of this handler. A higher priority will be considered before lower priority.
    */
  def priority: Priority

  override def compare(that: Handler): Int = priority.compare(that.priority)
}

object Handler {
  def path(paths: Set[String], handler: HttpHandler, priority: Priority = Priority.Normal): Handler = {
    val p = priority
    new Handler {
      def isURLMatch(url: URL): Boolean = paths.contains(url.path)

      override def handleRequest(url: URL, exchange: HttpServerExchange): Unit = handler.handleRequest(exchange)

      override def priority: Priority = p
    }
  }

  def apply(handler: HttpHandler, priority: Priority = Priority.Normal): Handler = {
    val p = priority
    new Handler {
      def isURLMatch(url: URL): Boolean = true

      override def handleRequest(url: URL, exchange: HttpServerExchange): Unit = handler.handleRequest(exchange)

      override def priority: Priority = p
    }
  }
}