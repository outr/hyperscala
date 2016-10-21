package org.hyperscala

import com.outr.scribe.Logging
import io.undertow.server.session.{SessionAttachmentHandler, SessionConfig, SessionCookieConfig, Session => UndertowSession}
import io.undertow.server.{HttpHandler, HttpServerExchange}
import io.undertow.util.{Headers, Sessions, StatusCodes}
import io.undertow.websockets.WebSocketConnectionCallback
import io.undertow.{Handlers, Undertow, UndertowOptions}
import pl.metastack.metarx.Sub

import scala.concurrent.duration._
import scala.language.experimental.macros
import scala.language.implicitConversions

class Server extends Logging with HttpHandler {
  object config {
    val autoRestart: Sub[Boolean] = sub(true)
    val host: Sub[String] = sub("0.0.0.0")
    val port: Sub[Int] = sub(8080)

    object session {
      val domain: Sub[Option[String]] = sub(None)
      val maxAge: Sub[FiniteDuration] = sub(0.seconds)
    }

    private def sub[T](value: T): Sub[T] = {
      val s = Sub[T](value)
      s.silentAttach { value =>
        if (autoRestart.get && isStarted) {
          restart()
        }
      }
      s
    }
  }

  private var instance: Option[Undertow] = None
  private var handlers = List.empty[Handler]
  private val sessionManager = new io.undertow.server.session.InMemorySessionManager("ServerSessionManager")
  private val sessionConfig = new SessionCookieConfig
  private val sessionAttachmentHandler = new SessionAttachmentHandler(sessionManager, sessionConfig) {
    setNext(Server.this)
  }
  private lazy val sessionCookieConfig = new SessionCookieConfig {
    config.session.domain.get.foreach(setDomain)
    setMaxAge(config.session.maxAge.get.toSeconds.toInt)
  }
  val resourceManager = new FunctionalResourceManager(this)
  private val resourceHandler = new FunctionalResourceHandler(resourceManager)
  register(resourceHandler)

  var errorHandler: Handler = DefaultErrorHandler

  def start(): Unit = synchronized {
    val server = Undertow.builder()
      .setServerOption(UndertowOptions.ENABLE_HTTP2, java.lang.Boolean.TRUE)
      .addHttpListener(config.port.get, config.host.get)
      .setHandler(sessionAttachmentHandler)
      .build()
    try {
      server.start()
    } catch {
      case t: Throwable => throw new RuntimeException(s"Failed to start server at ${config.host.get}:${config.port.get}.", t)
    }
    instance = Some(server)
    logger.info(s"Server started on ${config.host.get}:${config.port.get}...")
  }

  def isStarted: Boolean = instance.nonEmpty

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

  def restart(): Unit = synchronized {
    stop()
    start()
  }

  def register(handler: Handler): Handler = synchronized {
    handlers = (handler :: handlers).sorted
    handler
  }

  def unregister(handler: Handler): Unit = synchronized {
    handlers = handlers.filterNot(_ eq handler)
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

  def apply(app: WebApplication): Server = {
    // Instantiate Server
    val server = new Server {
      override def error(t: Throwable): Unit = app.error(t)
    }

    // Create WebSocket communication handler
    val webSocketCallback = app.appManager.asInstanceOf[WebSocketConnectionCallback]
    Handler.pathMatch(app.communicationPath).withHandler(Handlers.websocket(webSocketCallback)).register(server)

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