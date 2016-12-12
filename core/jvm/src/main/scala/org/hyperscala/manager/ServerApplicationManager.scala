package org.hyperscala.manager

import com.outr.scribe.Logging
import io.undertow.websockets.WebSocketConnectionCallback
import io.undertow.websockets.core._
import io.undertow.websockets.spi.WebSocketHttpExchange
import org.hyperscala.{Connection, PartialSupport, ReloadRequest, Request, RequestValidator, ScreenContentResponse, Server, ServerScreen, URL, Unique, ValidationResult, WebApplication}

class ServerApplicationManager(val app: WebApplication) extends WebSocketConnectionCallback with ApplicationManager {
  private val currentConnection = new ThreadLocal[Option[Connection]] {
    override def initialValue(): Option[Connection] = None
  }
  private var unboundConnections = Map.empty[String, ServerConnection]

  def createConnection(url: URL): ServerConnection = synchronized {
    val c = new ServerConnection(this, url)
    unboundConnections += c.id -> c
    c.init()
    c
  }

  def bindConnection(exchange: WebSocketHttpExchange, channel: WebSocketChannel): Unit = synchronized {
    val id = exchange.getQueryString
    unboundConnections.get(id) match {
      case Some(c) => {
        channel.getReceiveSetter.set(c)
        channel.resumeReceives()
        unboundConnections -= id
        _connections += c
        c.bind(exchange, channel)
      }
      case None => {    // Unable to find the connection id. Mostly happens if navigating back to an old cached URL.
        logger.info(s"Unbound connection not found by id ($id). Requesting client reload page.")
        channel.getReceiveSetter.set(new AbstractReceiveListener {
        })
        channel.resumeReceives()

        // Request a page reload
        val reloadPickler = app.picklerForChannel(app.reloadRequest).get
        val json = reloadPickler.write(ReloadRequest(force = true))
        val message = s"${reloadPickler.id}:$json"
        WebSockets.sendText(message, channel, None.orNull)
      }
    }
  }

  private[hyperscala] var _connections = Set.empty[Connection]
  override def connections: Set[Connection] = _connections

  override def connectionOption: Option[Connection] = currentConnection.get()

  override def onConnect(exchange: WebSocketHttpExchange, channel: WebSocketChannel): Unit = {
    logger.info("WebSocket connected!")

    bindConnection(exchange, channel)
  }

  def using[R](connection: Connection)(f: => R): R = {
    val previous = currentConnection.get()
    currentConnection.set(Option(connection))
    try {
      Server.withServerSession(connection.serverSession) {
        f
      }
    } finally {
      currentConnection.set(previous)
    }
  }

  override def connection: ServerConnection = super.connection.asInstanceOf[ServerConnection]

  override def init(): Unit = {
    app.urlChanged.attach { evt =>
      val previousScreen = connection.screen.get
      val newScreen = app.byURL(evt.url)
      logger.debug(s"Path Changed: $evt, previous: $previousScreen, new: $newScreen")
      if (previousScreen != newScreen) {
        previousScreen.asInstanceOf[ServerScreen].deactivate(connection)
        val serverScreen = newScreen.asInstanceOf[ServerScreen]
        serverScreen.activate(connection)
        logger.debug(s"Activated: $serverScreen")
        connection.screen := newScreen
      }
    }
    app.screenContentRequest.attach { evt =>
      if (connection.url.get != evt.url) {
        connection.url := evt.url
      }
      val screen = app.byName(evt.screenName).getOrElse(throw new RuntimeException(s"Unable to find screen by name: ${evt.screenName} (${app.screens.map(_.screenName).mkString(", ")})."))
      val serverScreen = screen.asInstanceOf[ServerScreen]
      val title = serverScreen.title()
      val request = Request(Right(connection.exchange))
      val validationResult = serverScreen match {
        case validator: RequestValidator => validator.validate(request)
        case _ => ValidationResult.Continue
      }
      validationResult match {
        case ValidationResult.Continue => {
          val html = serverScreen.html(request, partial = true)
          app.screenContentResponse := ScreenContentResponse(title, html, evt.screenName, serverScreen.asInstanceOf[PartialSupport].partialParentId, evt.replace)
        }
        case ValidationResult.Redirect(location) => {
          app.reloadRequest := ReloadRequest(force = false, url = Some(location))
        }
        case ValidationResult.Error(status, message) => {
          logger.info(s"Error: $status, $message - forcing screen reload!")
          app.reloadRequest := ReloadRequest(force = true)
        }
      }
    }
  }
}

class ServerConnection(manager: ServerApplicationManager, val initialURL: URL) extends AbstractReceiveListener with Connection with Logging {
  val id: String = Unique()
  val created: Long = System.currentTimeMillis()
  var lastActive: Long = System.currentTimeMillis()

  var exchange: WebSocketHttpExchange = _
  private[manager] val serverSession = Server.session.undertowSession()
  private var backlog = List.empty[String]
  private var _channel: WebSocketChannel = _
  def channel: WebSocketChannel = _channel

  override def app: WebApplication = manager.app

  override def init(): Unit = {}

  def bind(exchange: WebSocketHttpExchange, channel: WebSocketChannel): Unit = {
    this.exchange = exchange
    this._channel = channel

    backlog.reverse.foreach(send)
    backlog = Nil
  }

  override def send(id: Int, json: String): Unit = synchronized {
    val message = s"$id:$json"
    Option(_channel) match {
      case Some(c) => send(message)
      case None => backlog = message :: backlog
    }
  }

  def send(message: String): Unit = synchronized {
    WebSockets.sendText(message, _channel, None.orNull)
  }

  override def onFullTextMessage(channel: WebSocketChannel, message: BufferedTextMessage): Unit = manager.app.errorSupport {
    manager.using(this) {
      val data = message.getData
      logger.debug(s"Received: $data")
      val index = data.indexOf(':')
      if (index == -1) {
        logger.error(s"Ignoring invalid message: $data")
      } else {
        val id = data.substring(0, index).toInt
        val json = data.substring(index + 1)
        receive(id, json)
      }
    }
  }

  override def onClose(webSocketChannel: WebSocketChannel, channel: StreamSourceFrameChannel): Unit = {
    super.onClose(webSocketChannel, channel)

    manager.synchronized {
      manager._connections -= this
    }
  }
}