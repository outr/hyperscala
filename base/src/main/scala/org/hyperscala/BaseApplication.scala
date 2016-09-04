package org.hyperscala

import pl.metastack.metarx.Channel

import scala.language.experimental.macros

trait BaseApplication {
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

  protected[hyperscala] var picklers: Vector[Pickler[_]]

  final def manager(): ApplicationManager = macro BaseMacros.applicationManager
  protected[hyperscala] def add[T](pickler: Pickler[T]): Unit
  protected[hyperscala] def add(screen: BaseScreen): Unit

  protected def register[T]: Channel[T] = macro BaseMacros.appPickler[T]

  def byPath(path: String): BaseScreen
}
