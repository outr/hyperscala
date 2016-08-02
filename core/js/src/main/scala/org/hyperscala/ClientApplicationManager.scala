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

  private var popping = false

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

    updateScreen()

    val s = screen.get.get.asInstanceOf[ClientScreen]
    s.load(None)

    // Register listener for Screen content
    app.screenContent.attach { evt =>
      val s = app.screens.find(_.isPathMatch(evt.path)).asInstanceOf[Option[ClientScreen]]
      logger.debug(s"Received Screen content: ${evt.content} for ${evt.path} (screen: $s)")
      s.get.load(Some(evt))
    }

    // Listen for history changes
    window.addEventListener("popstate", (evt: PopStateEvent) => {
      popping = true
      try {
        updateState()
      } finally {
        popping = false
      }
    })

    // Listen for screen changes
    screen.attach(screenChanged)
  }

  private var previous: Option[BaseScreen] = None

  private def updateScreen(): Option[ClientScreen] = {
    val path = document.location.pathname
    val s = app.screens.find(_.isPathMatch(path)).asInstanceOf[Option[ClientScreen]]
    if (screen.get != s) {
      screen := s
    }
    s
  }

  private var firstScreen = true

  private def screenChanged(screenOption: Option[BaseScreen]): Unit = {
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
            val path = s.path
            if (firstScreen) {
              firstScreen = false
            } else if (!popping) {
              if (replace) {
                logger.info(s"Replacing state: $path")
                window.history.replaceState(path, path, path)
              } else {
                logger.info(s"Pushing state: $path")
                window.history.pushState(path, path, path)
              }
            }
            updateState()
            s.show()
          }
        }
        case None => // Nothing to do
      }
      previous = screenOption
    }
  }

  private var previousState: String = ""

  private def updateState(): Unit = if (document.location.pathname != previousState) {
    logger.info(s"Updating state from ${previousState} to ${document.location.pathname}")
    previousState = document.location.pathname
    val s = updateScreen()
    app.pathChanged := PathChanged(document.location.pathname, requestContent = !s.get.loaded)
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