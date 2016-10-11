package org.hyperscala.communication.server

import io.undertow.Handlers
import io.undertow.util.Headers
import io.undertow.websockets.WebSocketConnectionCallback
import io.undertow.websockets.core.{AbstractReceiveListener, BufferedTextMessage, StreamSourceFrameChannel, WebSocketChannel, WebSockets}
import io.undertow.websockets.spi.WebSocketHttpExchange
import org.hyperscala.communication.Communication
import org.hyperscala.{Handler, Server}
import pl.metastack.metarx.Channel

class CommunicationServer(path: String,
                          authorization: WebSocketHttpExchange => Option[String] = (exchange: WebSocketHttpExchange) => None
                         ) extends WebSocketConnectionCallback with Communication {
  private val _connection = new ThreadLocal[CommunicationServerConnection]
  def connection: CommunicationServerConnection = _connection.get()
  private var _connections = Set.empty[CommunicationServerConnection]

  def connections: Set[CommunicationServerConnection] = _connections

  def withConnection[R](connection: CommunicationServerConnection)(f: => R): R = {
    val previousState = this.connection
    this._connection.set(connection)
    try {
      f
    } finally {
      this._connection.set(previousState)
    }
  }

  /**
    * Pass messages to this to send through the server to the client.
    */
  val send: Channel[String] = Channel[String]
  /**
    * Listen to receive messages from the client sent to this server.
    */
  val receive: Channel[String] = Channel[String]

  /**
    * Pass messages to this to send through the server to all connected clients.
    */
  val broadcast: Channel[String] = Channel[String]

  send.attach { message =>
    connection.send := message
  }
  broadcast.attach { message =>
    connections.foreach { c =>
      withConnection(c) {
        send := message
      }
    }
  }

  def register(server: Server): Unit = {
    Handler.pathMatch(path).withHandler(Handlers.websocket(this)).register(server)
  }

  override def onConnect(exchange: WebSocketHttpExchange, channel: WebSocketChannel): Unit = synchronized {
    val authorized = authorization(exchange) match {
      case Some(auth) => exchange.getRequestHeader(Headers.AUTHORIZATION_STRING) == auth
      case None => true
    }
    if (authorized) {
      val c = new CommunicationServerConnection(this, channel)
      channel.getReceiveSetter.set(c)
      channel.resumeReceives()
      _connections += c
    } else {
      // TODO: throw an error
    }
  }

  private[server] def remove(connection: CommunicationServerConnection): Unit = synchronized {
    _connections -= connection
  }
}

class CommunicationServerConnection(server: CommunicationServer, channel: WebSocketChannel) extends AbstractReceiveListener {
  val created = System.currentTimeMillis()

  /**
    * Pass messages to this to send through the server to the client.
    */
  val send: Channel[String] = Channel[String]

  /**
    * Listen to receive messages from the client sent to this server.
    */
  val receive: Channel[String] = Channel[String]

  send.attach { message =>
    WebSockets.sendText(message, channel, None.orNull)
  }
  receive.attach { message =>
    server.withConnection(this) {
      server.receive := message
    }
  }

  override def onFullTextMessage(channel: WebSocketChannel, message: BufferedTextMessage): Unit = {
    val data = message.getData
    receive := data
  }

  override def onClose(webSocketChannel: WebSocketChannel, channel: StreamSourceFrameChannel): Unit = {
    server.remove(this)
  }
}

case class ConnectionMessage(message: String, connection: CommunicationServerConnection)