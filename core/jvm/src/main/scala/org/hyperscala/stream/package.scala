package org.hyperscala

import org.hyperscala.delta.{Delta, Selector}

import scala.language.implicitConversions

package object stream {
  implicit def delta2StreamDelta(delta: Delta): StreamDelta = new StreamDelta(delta)
  implicit def selector2StreamSelector(selector: Selector): StreamSelector = new StreamSelector(selector)
}