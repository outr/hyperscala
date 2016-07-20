package org.hyperscala

import pl.metastack.metarx.Channel

import scala.annotation.compileTimeOnly
import scala.language.experimental.macros
import scala.reflect.macros.blackbox

@compileTimeOnly("Enable macro paradise to expand macro annotations")
object Macros {
  def pickler[T](c: blackbox.Context)(implicit t: c.WeakTypeTag[T]): c.Expr[Channel[T]] = {
    import c.universe._

    c.Expr[Channel[T]](
      q"""
          val pickler = new Pickler[$t](${c.prefix.tree}) {
            override protected[hyperscala] def read(json: String): $t = upickle.default.read[$t](json)
            override protected[hyperscala] def write(t: $t): String = upickle.default.write[$t](t)
          }
          pickler.channel
       """
    )
  }
}

abstract class Pickler[T](val screen: Screen) {
  private[hyperscala] val receiving = new ThreadLocal[Boolean] {
    override def initialValue(): Boolean = false
  }

  val id = screen.app.add(this)
  val channel: Channel[T] = Channel[T]

  protected[hyperscala] def read(json: String): T
  protected[hyperscala] def write(t: T): String

  private[hyperscala] def receive(json: String): Unit = {
    val t = read(json)
    receiving.set(true)
    try {
      channel := t
    } finally {
      receiving.set(false)
    }
  }
}