package org.hyperscala.manager

import com.outr.scribe.Logging
import org.hyperscala.{BaseScreen, ClientScreen, ScreenContentRequest, URL, _}
import org.scalajs.dom._
import org.scalajs.dom.raw.WebSocket

class ClientApplicationManager(app: WebApplication) extends ApplicationManager {
  private lazy val _connection = new ClientConnection(app)

  override def connections: Set[Connection] = Set(_connection)

  override def connectionOption: Option[Connection] = Option(_connection)

  override def init(): Unit = _connection.init()
}

class ClientConnection(val app: WebApplication) extends Connection with Logging {
  private lazy val connectionId = byId[html.Input]("hyperscala-connection-id").value
  private lazy val webSocket = new WebSocket(s"ws://${window.location.host}${app.communicationPath}?$connectionId")

  private var connected = false
  private var queue = List.empty[String]

  private var popping = false

  override def initialURL: URL = URL(document.location.href)

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
        try {
          receive(id, json)
        } catch {
          case t: Throwable => app.error(t)
        }
      }
    }

    updateScreen()

    // Loading the current screen
    screen.get.asInstanceOf[ClientScreen].load(None)

    // Register listener for Screen content
    app.screenContentResponse.attach { evt =>
      val screen = app.byName(evt.screenName).getOrElse(throw new RuntimeException(s"Unable to find screen by name: ${evt.screenName}.")).asInstanceOf[ClientScreen]
      logger.debug(s"Received Screen content: ${evt.content} for ${evt.screenName} (screen: $screen)")
      try {
        replace = evt.replace
        screen.load(Some(evt))
      } finally {
        replace = false
      }
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

  def updateScreen(): ClientScreen = {
    val url = URL(document.location.href)
    val s = app.byURL(url)
    if (screen.get != s) {
      screen := s
    }
    s.asInstanceOf[ClientScreen]
  }

  private def screenChanged(newScreen: BaseScreen): Unit = {
    logger.info(s"screenChanged: $newScreen from $previous")
    if (!previous.contains(newScreen)) {
      previous match {
        case Some(scrn) => scrn match {
          case s: ClientScreen => s.doDeactivate()
        }
        case None => // No previous screen defined
      }
      val s = newScreen.asInstanceOf[ClientScreen]
      if (!s.loaded) {
        app.screenContentRequest := ScreenContentRequest(s.screenName, URL(document.location.href), replace)
      }
      updateState()
      s.show()
      previous = Some(s)
    }
  }

  private var previousState: String = ""

  def updateState(): Unit = if (document.location.href != previousState) {
    logger.info(s"Updating state from ${previousState} to ${document.location.href}")
    previousState = document.location.href
    app.urlChanged := URLChanged(URL(document.location.href))
    updateScreen()
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