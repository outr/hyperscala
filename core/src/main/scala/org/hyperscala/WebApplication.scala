package org.hyperscala

import com.outr.scribe.Logging
import pl.metastack.metarx.Channel

import scala.language.experimental.macros

abstract class WebApplication extends BaseApplication with Logging {
  object global extends MapStore {
    private var _map: Map[String, Any] = Map.empty
    override def map: Map[String, Any] = _map

    override def update[T](key: String, value: T): Unit = synchronized {
      _map += key -> value
    }

    override def remove(key: String): Unit = synchronized {
      _map -= key
    }
  }

  override protected[hyperscala] var picklers = Vector.empty[Pickler[_]]
  private var _screens = Vector.empty[BaseScreen]
  private[hyperscala] var screensByName = Map.empty[String, Screen]

  val pathChanged: Channel[PathChanged] = register[PathChanged]
  val screenContentRequest: Channel[ScreenContentRequest] = register[ScreenContentRequest]
  val screenContentResponse: Channel[ScreenContentResponse] = register[ScreenContentResponse]

  def screens: Vector[Screen] = _screens.asInstanceOf[Vector[Screen]]

  def connection: Connection = appManager.connection

  def create[S <: Screen]: S = macro Macros.screen[S]
  def server[S <: Screen]: S = macro Macros.serverScreen[S]
  def communicationPath: String = "/communication"

  override protected[hyperscala] def add(screen: BaseScreen): Unit = synchronized {
    _screens = (_screens :+ screen).sortBy(_.priority)
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
    pathChanged.attach { evt =>
      connection.path := evt.path
    }
    appManager.init()
  }

  def byPath(path: String): Screen = {
    screens.find(_.isPathMatch(path)).getOrElse(throw new RuntimeException(s"No screen found for the specified path: $path."))
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