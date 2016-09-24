package org.hyperscala.communication

import org.hyperscala.{BaseMacros, Pickler, Picklers}
import pl.metastack.metarx.Channel

import scala.language.experimental.macros

trait Communication extends Picklers {
  private val PicklerMessageRegex = """(\d+):(.+)""".r

  protected[communication] var picklers = Vector.empty[Pickler[_]]

  val send: Channel[String]
  val receive: Channel[String]

  override protected[hyperscala] def add[T](pickler: Pickler[T]): Int = synchronized {
    val position = picklers.length
    picklers = picklers :+ pickler
    pickler.channel.attach { t =>
      if (!pickler.isReceiving(t)) {
        val json = pickler.write(t)
        send := s"$position:$json"
      }
    }
    receive.attach {
      case PicklerMessageRegex(id, json) => picklers(id.toInt).receive(json)
      case _ => // Ignore others
    }
    position
  }

  protected def register[T]: Channel[T] = macro BaseMacros.communicationPickler[T]
}
