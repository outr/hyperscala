package org.hyperscala

import com.outr.scribe.Logging
import io.undertow.websockets.WebSocketConnectionCallback
import io.undertow.websockets.core.{AbstractReceiveListener, BufferedTextMessage, WebSocketChannel, WebSockets}
import io.undertow.websockets.spi.WebSocketHttpExchange

class ServerConnectionManager(val app: WebApplication) extends WebSocketConnectionCallback with ConnectionManager {
  private val currentConnection = new ThreadLocal[Option[Connection]] {
    override def initialValue(): Option[Connection] = None
  }

  private var _connections = Set.empty[Connection]
  override def connections: Set[Connection] = _connections

  override def connectionOption: Option[Connection] = currentConnection.get()

  override def init(): Unit = {}

  override def onConnect(exchange: WebSocketHttpExchange, channel: WebSocketChannel): Unit = {
    logger.info("WebSocket connected!")
    val connection = new ServerConnection(this, channel)
    synchronized {
      _connections += connection
    }
    channel.getReceiveSetter.set(connection)

  }

  def using[R](connection: Connection)(f: => R): R = {
    currentConnection.set(Option(connection))
    try {
      f
    } finally {
      currentConnection.remove()
    }
  }
}

class ServerConnection(manager: ServerConnectionManager, channel: WebSocketChannel) extends AbstractReceiveListener with Connection with Logging {
  override def app: WebApplication = manager.app

  override def init(): Unit = {}

  override def send(id: Int, json: String): Unit = WebSockets.sendText(s"$id:$json", channel, None.orNull)

  override def onFullTextMessage(channel: WebSocketChannel, message: BufferedTextMessage): Unit = {
    val data = message.getData
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
}