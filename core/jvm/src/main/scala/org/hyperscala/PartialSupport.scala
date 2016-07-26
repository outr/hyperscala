package org.hyperscala

import org.hyperscala.stream.Selector

trait PartialSupport {
  this: ServerScreen =>

  def partialParentId: String

  def partialSelector: Selector
}
