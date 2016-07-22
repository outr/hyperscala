package org.hyperscala

import pl.metastack.metarx.Channel

import scala.annotation.compileTimeOnly
import scala.reflect.macros.blackbox

@compileTimeOnly("Enable macro paradise to expand macro annotations")
object BaseMacros {
  def pickler[T](c: blackbox.Context)(implicit t: c.WeakTypeTag[T]): c.Expr[Channel[T]] = {
    import c.universe._

    c.Expr[Channel[T]](
      q"""
          val pickler = new org.hyperscala.Pickler[$t](${c.prefix.tree}) {
            override def read(json: String): $t = upickle.default.read[$t](json)
            override def write(t: $t): String = upickle.default.write[$t](t)
          }
          pickler.channel
       """
    )
  }

  def applicationManager(c: blackbox.Context)(): c.Expr[ApplicationManager] = {
    import c.universe._

    val isJS = try {
      c.universe.rootMirror.staticClass("scala.scalajs.js.Any")
      true
    } catch {
      case t: Throwable => false
    }

    val manager = if (isJS) {
      q"""new org.hyperscala.ClientApplicationManager(${c.prefix.tree})"""
    } else {
      q"""new org.hyperscala.ServerApplicationManager(${c.prefix.tree})"""
    }

    c.Expr[ApplicationManager](manager)
  }
}
