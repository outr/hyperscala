package org.hyperscala

import com.outr.scribe.Logging
import org.hyperscala.manager.ApplicationManager
import pl.metastack.metarx.Channel

import scala.language.experimental.macros

abstract class WebApplication extends BaseApplication with Logging {
  Channel.ValidateCyclical = false

  protected[hyperscala] var picklers = Vector.empty[Pickler[_]]
  private var _screens = Vector.empty[Screen]
  private[hyperscala] var screensByName = Map.empty[String, Screen]

  val appManager: ApplicationManager = AppManagerCreator.create(this)
  val urlChanged: Channel[URLChanged] = register[URLChanged]
  val screenContentRequest: Channel[ScreenContentRequest] = register[ScreenContentRequest]
  val screenContentResponse: Channel[ScreenContentResponse] = register[ScreenContentResponse]

  def screens: Vector[Screen] = _screens

  def connection: Connection = appManager.connection

  def create[S <: Screen]: S = macro Macros.screen[S]
  def server[S <: Screen]: S = macro Macros.serverScreen[S]
  def communicationPath: String = "/communication"

  protected[hyperscala] def add(screen: Screen): Unit = synchronized {
    _screens = (_screens :+ screen).sortBy(_.priority)
    screensByName += screen.screenName -> screen
  }

  protected[hyperscala] def add[T](pickler: Pickler[T]): Unit = synchronized {
    val position = picklers.length
    picklers = picklers :+ pickler
    pickler.channel.attach { t =>
      if (!pickler.isReceiving(t)) {
        val json = pickler.write(t)
        appManager.connection.send(position, json)
      }
    }
  }

  def init(): Unit = errorSupport {
    urlChanged.attach { evt =>
      connection.url := URL(evt.url)
    }
    appManager.init()
  }

  def byURL(url: URL): Screen = {
    screens.find(_.isURLMatch(url)).getOrElse(throw new RuntimeException(s"No screen found for the specified url: $url."))
  }

  def byName(screenName: String): Option[Screen] = screensByName.get(screenName)

  def createPath(path: String, args: Map[String, String] = Map.empty): String = {
    val b = new StringBuilder(path)
    if (args.nonEmpty) {
      b.append('?')
      val params = args.map {
        case (key, value) => s"$key=${Util.encodeURIComponent(value)}"
      }.mkString("&")
      b.append(params)
    }
    b.toString()
  }

  def errorSupport[R](f: => R): R = try {
    f
  } catch {
    case t: Throwable => {
      error(t)
      throw t
    }
  }
  def error(t: Throwable): Unit = logger.error(t)
}