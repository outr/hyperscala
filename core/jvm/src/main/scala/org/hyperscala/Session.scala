package org.hyperscala

import scala.annotation.compileTimeOnly
import scala.reflect.macros.blackbox

trait Session

@compileTimeOnly("Enable macro paradise to expand macro annotations")
object Session {
  def session[S <: Session](c: blackbox.Context)(implicit s: c.WeakTypeTag[S]): c.Expr[S] = {
    import c.universe._

    c.Expr[S](
      q"""
         val sessionKey = ${s.tpe.toString}
         ${c.prefix.tree}.serverSession match {
            case Some(ss) => {
              Option(ss.getAttribute(sessionKey).asInstanceOf[$s]) match {
                case Some(s) => s
                case None => {
                  val s = new $s()
                  ss.setAttribute(sessionKey, s)
                  s
                }
              }
            }
            case None => throw new RuntimeException("No server session defined!")
         }
       """)
  }
}