package org.hyperscala

import com.outr.scribe.Logging
import io.undertow.server.session.{SessionAttachmentHandler, SessionConfig, SessionCookieConfig, Session => UndertowSession}
import io.undertow.server.{HttpHandler, HttpServerExchange}
import io.undertow.util.{Headers, Sessions, StatusCodes}
import io.undertow.websockets.WebSocketConnectionCallback
import io.undertow.{Handlers, Undertow, UndertowOptions}

import scala.concurrent.duration._
import scala.language.experimental.macros
import scala.language.implicitConversions

class Server(host: String, port: Int, sessionDomain: Option[String] = None, sessionMaxAge: FiniteDuration = 0.seconds) extends Logging with HttpHandler {
  private var instance: Option[Undertow] = None
  private var handlers = List.empty[Handler]
  private val sessionManager = new io.undertow.server.session.InMemorySessionManager("ServerSessionManager")
  private val sessionConfig = new SessionCookieConfig
  private val sessionAttachmentHandler = new SessionAttachmentHandler(sessionManager, sessionConfig) {
    setNext(Server.this)
  }
  private val sessionCookieConfig = new SessionCookieConfig {
    sessionDomain.foreach(setDomain)
    setMaxAge(sessionMaxAge.toSeconds.toInt)
  }
  val resourceManager = new FunctionalResourceManager(this)
  private val resourceHandler = new FunctionalResourceHandler(resourceManager)
  register(resourceHandler)

  var errorHandler: Handler = DefaultErrorHandler

  def start(): Unit = synchronized {
    val server = Undertow.builder()
      .setServerOption(UndertowOptions.ENABLE_HTTP2, java.lang.Boolean.TRUE)
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

  def register(handler: Handler): Handler = synchronized {
    handlers = (handler :: handlers).sorted
    handler
  }

  def register(handler: HttpHandler, paths: String*): Handler = {
    logger.info(s"Registering ${paths.mkString(", ")}")
    register(Handler.path(paths.toSet, handler))
  }

  def register(path: String, contentType: String, f: HttpServerExchange => String): Handler = {
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

  override def handleRequest(exchange: HttpServerExchange): Unit = {
    exchange.putAttachment(SessionConfig.ATTACHMENT_KEY, sessionCookieConfig)
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

object DefaultErrorHandler extends Handler {
  override def isURLMatch(url: URL): Boolean = false

  override def priority: Priority = Priority.Normal

  override def handleRequest(url: URL, exchange: HttpServerExchange): Unit = {
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