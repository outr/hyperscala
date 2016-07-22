package org.hyperscala

import com.outr.scribe.Logging
import io.undertow.websockets.WebSocketConnectionCallback
import io.undertow.websockets.core.{AbstractReceiveListener, BufferedTextMessage, StreamSourceFrameChannel, WebSocketChannel, WebSockets}
import io.undertow.websockets.spi.WebSocketHttpExchange

class ServerApplicationManager(val app: WebApplication) extends WebSocketConnectionCallback with ApplicationManager {
  private val currentConnection = new ThreadLocal[Option[Connection]] {
    override def initialValue(): Option[Connection] = None
  }

  private[hyperscala] var _connections = Set.empty[Connection]
  override def connections: Set[Connection] = _connections

  override def connectionOption: Option[Connection] = currentConnection.get()

  override def onConnect(exchange: WebSocketHttpExchange, channel: WebSocketChannel): Unit = {
    logger.info("WebSocket connected!")
    val connection = new ServerConnection(this, channel)
    synchronized {
      _connections += connection
    }
    channel.getReceiveSetter.set(connection)
    channel.resumeReceives()
  }

  def using[R](connection: Connection)(f: => R): R = {
    currentConnection.set(Option(connection))
    try {
      f
    } finally {
      currentConnection.remove()
    }
  }

  override def init(): Unit = {}
}

class ServerConnection(manager: ServerApplicationManager, channel: WebSocketChannel) extends AbstractReceiveListener with Connection with Logging {
  override def app: WebApplication = manager.app

  override def init(): Unit = {
    manager.synchronized {
      manager._connections += this
    }
  }

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

  override def onClose(webSocketChannel: WebSocketChannel, channel: StreamSourceFrameChannel): Unit = {
    super.onClose(webSocketChannel, channel)

    manager.synchronized {
      manager._connections -= this
    }
  }
}