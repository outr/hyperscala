package org.hyperscala.communication.client

import java.net.URI
import java.util

import io.undertow.server.DefaultByteBufferPool
import io.undertow.util.Headers
import io.undertow.websockets.client.WebSocketClient.ConnectionBuilder
import io.undertow.websockets.client.{WebSocketClient, WebSocketClientNegotiation}
import io.undertow.websockets.core.{AbstractReceiveListener, BufferedTextMessage, WebSocketCallback, WebSocketChannel, WebSockets}
import org.hyperscala.{Pickler, Server}
import org.hyperscala.communication.Communication
import org.xnio.{OptionMap, Options, Xnio}
import pl.metastack.metarx.{Channel, Var}

import scala.collection.JavaConversions._

class CommunicationClient(uri: URI,
                          autoReconnect: Boolean = true,
                          directBuffer: Boolean = false,
                          bufferSize: Int = 2048,
                          workerThreads: Int = 2,
                          highWater: Int = 1000000,
                          lowWater: Int = 1000000,
                          coreThreads: Int = 30,
                          maxThreads: Int = 30,
                          noDelay: Boolean = true,
                          buffered: Boolean = true,
                          authorization: => Option[String] = None) extends Communication {
  private lazy val worker = Xnio.getInstance().createWorker(OptionMap.builder()
    .set(Options.WORKER_IO_THREADS, workerThreads)
    .set(Options.CONNECTION_HIGH_WATER, highWater)
    .set(Options.CONNECTION_LOW_WATER, lowWater)
    .set(Options.WORKER_TASK_CORE_THREADS, coreThreads)
    .set(Options.WORKER_TASK_MAX_THREADS, maxThreads)
    .set(Options.TCP_NODELAY, noDelay)
    .set(Options.CORK, buffered)
    .getMap
  )
  private lazy val bufferPool = new DefaultByteBufferPool(directBuffer, bufferSize)
  private lazy val connectionBuilder: ConnectionBuilder = WebSocketClient.connectionBuilder(worker, bufferPool, uri)
    .setClientNegotiation(new WebSocketClientNegotiation(null, null) {
      override def beforeRequest(headers: util.Map[String, util.List[String]]): Unit = {
        authorization.foreach { auth =>
          headers.put(Headers.AUTHORIZATION_STRING, List(auth))
        }
      }
    })

  private val _channel = Var[Option[WebSocketChannel]](None)
  def channel: WebSocketChannel = _channel.get.getOrElse(throw new RuntimeException("No connection has been established."))

  /**
    * Pass messages to this to send through the client to the server.
    */
  val send: Channel[String] = Channel[String]

  /**
    * Listen to receive messages from the server sent to this client.
    */
  val receive: Channel[String] = Channel[String]

  private var backlog = List.empty[String]

  def connect(): Unit = if (_channel.get.isEmpty) {
    _channel := Some(connectionBuilder.connect().get())

    channel.resumeReceives()

    // Receive messages
    channel.getReceiveSetter.set(new AbstractReceiveListener {
      override def onFullTextMessage(channel: WebSocketChannel, message: BufferedTextMessage): Unit = {
        val data = message.getData
        receive := data
      }

      override def onError(channel: WebSocketChannel, error: Throwable): Unit = {
        super.onError(channel, error)
        disconnected()
      }
    })

    // Send messages
    send.attach { message =>
      checkBacklog()
      sendMessage(message)
    }

    checkBacklog()
  }

  def disconnect(): Unit = {
    channel.close()
  }

  private def checkBacklog(): Unit = {
    if (backlog.nonEmpty) {
      synchronized {
        backlog.foreach(sendMessage)
        backlog = Nil
      }
    }
  }

  private def sendMessage(message: String): Unit = {
    WebSockets.sendText(message, channel, new WebSocketCallback[Void] {
      override def complete(channel: WebSocketChannel, context: Void): Unit = {
        // Successfully sent
      }

      override def onError(channel: WebSocketChannel, context: Void, throwable: Throwable): Unit = CommunicationClient.this synchronized {
        backlog = message :: backlog
      }
    })
  }

  private def disconnected(): Unit = {
    _channel := None

    if (autoReconnect) {
      connect()
    }
  }
}