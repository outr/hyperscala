package org.hyperscala

import pl.metastack.metarx.{Channel, StateChannel}

import scala.annotation.compileTimeOnly
import scala.language.experimental.macros
import scala.reflect.macros.blackbox

@compileTimeOnly("Enable macro paradise to expand macro annotations")
object Macros {
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

  def screen[S <: Screen](c: blackbox.Context)(implicit s: c.WeakTypeTag[S]): c.Expr[S] = {
    import c.universe._

    val isJS = try {
      c.universe.rootMirror.staticClass("scala.scalajs.js.Any")
      true
    } catch {
      case t: Throwable => false
    }

    val typeString = s.tpe.toString
    val (preType, postType) = if (typeString.indexOf('.') != -1) {
      val index = typeString.indexOf('.')
      typeString.substring(0, index + 1) -> typeString.substring(index + 1)
    } else {
      "" -> typeString
    }
    val screenTypeString = if (isJS) {
      s"${preType}Client$postType"
    } else {
      s"${preType}Server$postType"
    }
    val screenType = try {
      c.universe.rootMirror.staticClass(screenTypeString)
    } catch {
      case exc: ScalaReflectionException => c.abort(c.enclosingPosition, s"Unable to find implementation trait $screenTypeString for $typeString.")
    }
    c.Expr[S](
      q"""
         val webApp = ${c.prefix.tree}
         new $screenType {
          override def app = webApp
         }
       """)
  }

  def connectionManager(c: blackbox.Context)(): c.Expr[ConnectionManager] = {
    import c.universe._

    val isJS = try {
      c.universe.rootMirror.staticClass("scala.scalajs.js.Any")
      true
    } catch {
      case t: Throwable => false
    }

    val manager = if (isJS) {
      q"""new org.hyperscala.ClientConnectionManager(${c.prefix.tree})"""
    } else {
      q"""new org.hyperscala.ServerConnectionManager(${c.prefix.tree})"""
    }

    c.Expr[ConnectionManager](manager)
  }
}