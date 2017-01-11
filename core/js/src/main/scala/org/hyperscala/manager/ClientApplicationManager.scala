package org.hyperscala.manager

import com.outr.scribe.Logging
import org.hyperscala.{URL, _}
import org.scalajs.dom._
import org.scalajs.dom.raw.WebSocket

class ClientApplicationManager(app: WebApplication) extends ApplicationManager {
  private val _connection = new ClientConnection(app, URL(document.location.href))

  override def connections: Set[Connection] = Set(_connection)

  override def connectionOption: Option[Connection] = Option(_connection)

  override def init(): Unit = {
    app.reloadRequest.attach { r =>
      r.url match {
        case Some(url) => window.location.href = url
        case None => window.location.reload(r.force)
      }
    }
    _connection.init()
  }
}

class ClientConnection(val app: WebApplication, val initialURL: URL) extends Connection with Logging {
  private lazy val isSSL = window.location.protocol == "https:"
  private lazy val connectionId = byId[html.Input]("hyperscala-connection-id").value
  private lazy val webSocket = new WebSocket(s"${if (isSSL) "wss" else "ws"}://${window.location.host}${app.communicationPath}?$connectionId")

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
      window.setTimeout(() => {
        window.location.reload()
      }, 5000)
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

    updateScreen(urlChanged = false)

    // Loading the current screen
    screen.get.asInstanceOf[ClientScreen].load(None)

    // Register listener for Screen content
    app.screenContentResponse.attach { evt =>
      val screen = app.byName(evt.screenName).getOrElse(throw new RuntimeException(s"Unable to find screen by name: ${evt.screenName}.")).asInstanceOf[ClientScreen]
      logger.debug(s"Received Screen content for ${evt.screenName} (screen: $screen)")
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

  def pushURL(url: URL, force: Boolean = false): Unit = if (document.location.href != url.toString || force) app.siteType match {
    case SiteType.SinglePage if app.getByURL(url).nonEmpty => {
      val urlString = url.toString
      logger.debug(s"pushPath: $urlString screen: ${app.getByURL(url)}")
      window.history.pushState(urlString, urlString, urlString)
      updateState()
    }
    case _ => {     // Treat as multi-page
      window.location.href = url.toString
    }
  }

  def replaceURL(url: URL, force: Boolean = false): Unit = if (document.location.href != url.toString || force) app.siteType match {
    case SiteType.SinglePage if app.getByURL(url).nonEmpty => {
      val urlString = url.toString
      logger.debug(s"replacePath: $urlString screen: ${app.getByURL(url)}")
      window.history.replaceState(urlString, urlString, urlString)
      updateState()
    }
    case _ => {     // Treat as multi-page
      window.location.replace(url.toString)
    }
  }

  private var previous: Option[BaseScreen] = None

  def updateScreen(urlChanged: Boolean): ClientScreen = {
    val url = URL(document.location.href)
    val s = app.byURL(url).asInstanceOf[ClientScreen]
    if (screen.get != s) {
      screen := s
    } else if (urlChanged) {
      s.urlChanged(url)
    }
    s
  }

  private def screenChanged(newScreen: BaseScreen): Unit = {
    logger.debug(s"screenChanged: $newScreen from $previous")
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
    logger.debug(s"Updating state from ${previousState} to ${document.location.href}")
    previousState = document.location.href
    app.urlChanged := URLChanged(URL(document.location.href))
    updateScreen(urlChanged = true)
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