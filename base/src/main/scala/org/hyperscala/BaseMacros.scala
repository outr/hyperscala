package org.hyperscala

import org.hyperscala.manager.ApplicationManager
import pl.metastack.metarx.Channel

import scala.annotation.compileTimeOnly
import scala.reflect.macros.blackbox

@compileTimeOnly("Enable macro paradise to expand macro annotations")
object BaseMacros {
  def appPickler[T](c: blackbox.Context)(implicit t: c.WeakTypeTag[T]): c.Expr[Channel[T]] = {
    import c.universe._

    val app = q"${c.prefix.tree}"
    c.Expr[Channel[T]](
      q"""
          val pickler = new org.hyperscala.Pickler[$t]($app) {
            override def read(json: String): $t = upickle.default.read[$t](json)
            override def write(t: $t): String = upickle.default.write[$t](t)
          }
          pickler.channel
       """
    )
  }

  def screenPickler[T](c: blackbox.Context)(implicit t: c.WeakTypeTag[T]): c.Expr[Channel[T]] = {
    import c.universe._

    val app = q"${c.prefix.tree}.app"
    c.Expr[Channel[T]](
      q"""
          val pickler = new org.hyperscala.Pickler[$t]($app) {
            override def read(json: String): $t = upickle.default.read[$t](json)
            override def write(t: $t): String = upickle.default.write[$t](t)
          }
          pickler.channel
       """
    )
  }
}
