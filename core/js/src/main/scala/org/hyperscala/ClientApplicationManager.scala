package org.hyperscala

import com.outr.scribe.Logging
import org.scalajs.dom._
import org.scalajs.dom.raw.WebSocket

class ClientApplicationManager(app: WebApplication) extends ApplicationManager {
  private lazy val _connection = new ClientConnection(app)

  override def connections: Set[Connection] = Set(_connection)

  override def connectionOption: Option[Connection] = Option(_connection)

  override def init(): Unit = _connection.init()
}

class ClientConnection(val app: WebApplication) extends Connection with Logging {
  private lazy val url = s"ws://${window.location.host}${app.communicationPath}"
  private lazy val webSocket = new WebSocket(url)

  private var connected = false
  private var queue = List.empty[String]

  override def init(): Unit = {
    webSocket.onopen = (evt: Event) => {
      logger.info(s"WebSocket connection open")
      connected = true
      if (queue.nonEmpty) {
        queue.reverse.foreach { backlog =>
          webSocket.send(backlog)
        }
        queue = Nil
      }
    }
    webSocket.onerror = (evt: ErrorEvent) => {
      logger.info(s"WebSocket error: ${evt.message}")
    }
    webSocket.onclose = (evt: CloseEvent) => {
      logger.info(s"WebSocket connection closed")
    }
    webSocket.onmessage = (evt: MessageEvent) => {
      val messageData = evt.data.toString
      val index = messageData.indexOf(':')
      if (index == -1) {
        logger.error(s"Ignoring invalid message: $messageData")
      } else {
        val id = messageData.substring(0, index).toInt
        val json = messageData.substring(index + 1)
        receive(id, json)
      }
    }

    // TODO: listen for history change events
    val path = document.location.pathname
    screen := app.screens.find(_.isPathMatch(path))
    logger.info(s"Initialized. Path: $path, Screen: ${screen.get}")

    // Listen for screen changes
    var previous: Option[BaseScreen] = None
    var initialized = Set.empty[ClientScreen]
    screen.attach { screenOption =>
      logger.info(s"Screen changed: $screenOption")
      if (screenOption != previous) {
        previous match {
          case Some(scrn) => scrn match {
            case s: ClientScreen => s.deactivate()
          }
          case None => // No previous screen defined
        }
        screenOption match {
          case Some(scrn) => scrn match {
            case s: ClientScreen => {
              if (!initialized.contains(s)) {
                initialized += s
                s.init()
              }
              s.activate()
            }
          }
          case None => // Nothing to do
        }
        previous = screenOption
      }
    }
  }

  override def send(id: Int, json: String): Unit = {
    val message = s"$id:$json"
    if (connected) {
      webSocket.send(message)
    } else {
      queue = message :: queue
    }
  }
}