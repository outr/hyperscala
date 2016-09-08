package org.hyperscala

import pl.metastack.metarx.Channel

abstract class Pickler[T](app: BaseApplication) {
  private val receiving = new ThreadLocal[Option[T]] {
    override def initialValue(): Option[T] = None
  }

  val channel: Channel[T] = Channel[T]
  val id: Int = app.add(this)

  def isReceiving(t: T): Boolean = receiving.get().contains(t)

  def read(json: String): T
  def write(t: T): String

  def receive(json: String): Unit = {
    val t = read(json)
    receiving.set(Some(t))
    try {
      channel := t
    } finally {
      receiving.set(None)
    }
  }
}
