package org.hyperscala

import pl.metastack.metarx.Channel

abstract class Pickler[T](val screen: Screen) {
  private[hyperscala] val receiving = new ThreadLocal[Boolean] {
    override def initialValue(): Boolean = false
  }

  val id = screen.app.add(this)
  val channel: Channel[T] = Channel[T]

  def read(json: String): T
  def write(t: T): String

  def receive(json: String): Unit = {
    val t = read(json)
    receiving.set(true)
    try {
      channel := t
    } finally {
      receiving.set(false)
    }
  }
}
