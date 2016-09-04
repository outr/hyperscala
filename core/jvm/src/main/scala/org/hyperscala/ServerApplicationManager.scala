package org.hyperscala

import com.outr.scribe.Logging
import io.undertow.websockets.WebSocketConnectionCallback
import io.undertow.websockets.core.{AbstractReceiveListener, BufferedTextMessage, StreamSourceFrameChannel, WebSocketChannel, WebSockets}
import io.undertow.websockets.spi.WebSocketHttpExchange
import org.powerscala.Unique

class ServerApplicationManager private(val app: WebApplication) extends WebSocketConnectionCallback with ApplicationManager {
  private val currentConnection = new ThreadLocal[Option[Connection]] {
    override def initialValue(): Option[Connection] = None
  }
  private var unboundConnections = Map.empty[String, ServerConnection]

  def createConnection(path: String): ServerConnection = synchronized {
    val c = new ServerConnection(this, path)
    unboundConnections += c.id -> c
    c.init()
    c
  }

  def bindConnection(exchange: WebSocketHttpExchange, channel: WebSocketChannel): Unit = synchronized {
    val id = exchange.getQueryString
    val c = unboundConnections.getOrElse(id, throw new RuntimeException(s"No unbound connection found for $id."))
    channel.getReceiveSetter.set(c)
    channel.resumeReceives()
    unboundConnections -= id
    _connections += c
    c.bind(exchange, channel)
  }

  private[hyperscala] var _connections = Set.empty[Connection]
  override def connections: Set[Connection] = _connections

  override def connectionOption: Option[Connection] = currentConnection.get()

  override def onConnect(exchange: WebSocketHttpExchange, channel: WebSocketChannel): Unit = {
    logger.info("WebSocket connected!")

    bindConnection(exchange, channel)
  }

  def using[R](connection: Connection)(f: => R): R = {
    currentConnection.set(Option(connection))
    try {
      f
    } finally {
      currentConnection.remove()
    }
  }

  override def connection: ServerConnection = super.connection.asInstanceOf[ServerConnection]

  override def init(): Unit = {
    app.pathChanged.attach { evt =>
      val previousScreen = connection.screen.get
      val newScreen = app.byPath(evt.path)
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
      if (connection.path.get != evt.path) {
        connection.path := evt.path
      }
      val screen = app.byName(evt.screenName).getOrElse(throw new RuntimeException(s"Unable to find screen by name: ${evt.screenName}."))
      val serverScreen = screen.asInstanceOf[ServerScreen]
      val title = serverScreen.title()
      val html = serverScreen.html(Request(Right(connection.exchange)), partial = true)
      app.screenContentResponse := ScreenContentResponse(title, html, evt.screenName, serverScreen.asInstanceOf[PartialSupport].partialParentId)
    }
  }
}

object ServerApplicationManager {
  def apply(app: WebApplication): ServerApplicationManager = {
    app.global.getOrSet("applicationManager", new ServerApplicationManager(app))
  }
}

class ServerConnection(manager: ServerApplicationManager, val initialPath: String) extends AbstractReceiveListener with Connection with Logging {
  val id: String = Unique()
  val created: Long = System.currentTimeMillis()
  var lastActive: Long = System.currentTimeMillis()

  var exchange: WebSocketHttpExchange = _
  private val serverSession = Server.session.undertowSession
  private var backlog = List.empty[String]
  private var channel: WebSocketChannel = _

  override def app: BaseApplication = manager.app

  override def init(): Unit = {}

  def bind(exchange: WebSocketHttpExchange, channel: WebSocketChannel): Unit = {
    this.exchange = exchange
    this.channel = channel

    backlog.reverse.foreach(send)
    backlog = Nil
  }

  override def send(id: Int, json: String): Unit = synchronized {
    val message = s"$id:$json"
    Option(channel) match {
      case Some(c) => send(message)
      case None => backlog = message :: backlog
    }
  }

  def send(message: String): Unit = synchronized {
    WebSockets.sendText(message, channel, None.orNull)
  }

  override def onFullTextMessage(channel: WebSocketChannel, message: BufferedTextMessage): Unit = Server.withServerSession(serverSession) {
    val data = message.getData
    logger.debug(s"Received: $data")
    val index = data.indexOf(':')
    if (index == -1) {
      logger.error(s"Ignoring invalid message: $data")
    } else {
      val id = data.substring(0, index).toInt
      val json = data.substring(index + 1)
      manager.using(this) {
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