package org.hyperscala

import org.hyperscala.delta.Selector

trait PartialSupport {
  this: ServerScreen =>

  def partialParentId: String

  def partialSelector: Selector
}
