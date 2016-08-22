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
      logger.debug(s"Received: $messageData")
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
    app.screenContentResponse.attach { evt =>
      val screen = app.byName(evt.screenName).getOrElse(throw new RuntimeException(s"Unable to find screen by name: ${evt.screenName}.")).asInstanceOf[ClientScreen]
      logger.debug(s"Received Screen content: ${evt.content} for ${evt.screenName} (screen: $screen)")
      screen.load(Some(evt))
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

  def pushPath(path: String): Unit = {
    window.history.pushState(path, path, path)
    updateScreen()
  }

  def replacePath(path: String): Unit = {
    window.history.replaceState(path, path, path)
    updateScreen()
  }

  private var previous: Option[BaseScreen] = None

  def updateScreen(): Option[ClientScreen] = {
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
            logger.info(s"Changing screen to: $s")
            s.path match {
              case Some(path) => {
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
              }
              case None => // Screen doesn't affect history
            }
            if (!s.loaded) {
              app.screenContentRequest := ScreenContentRequest(s.screenName)
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
    val s = updateScreen().get
    app.pathChanged := PathChanged(document.location.pathname)
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