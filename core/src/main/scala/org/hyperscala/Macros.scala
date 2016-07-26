package org.hyperscala

import scala.annotation.compileTimeOnly
import scala.language.experimental.macros
import scala.reflect.macros.blackbox

@compileTimeOnly("Enable macro paradise to expand macro annotations")
object Macros {
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

          override def toString(): String = $screenTypeString
         }
       """)
  }

  def serverScreen[S <: Screen](c: blackbox.Context)(implicit s: c.WeakTypeTag[S]): c.Expr[S] = {
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
    if (isJS) {
      c.Expr[S](
        q"""
           val webApp = ${c.prefix.tree}
           new ${s.tpe} with org.hyperscala.ClientScreen {
              override def app = webApp

              override def init(): Unit = {}
              override def activate(): Unit = {}
              override def deactivate(): Unit = {}
              override def path: String = ""

              override def toString(): String = $screenTypeString
           }
         """)
    } else {
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

          override def toString(): String = $screenTypeString
         }
       """)
    }
  }
}