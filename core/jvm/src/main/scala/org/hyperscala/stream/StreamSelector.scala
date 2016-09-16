package org.hyperscala.stream

import org.hyperscala.delta.Selector
import org.hyperscala.delta.Selector._

class StreamSelector(selector: Selector) {
  def lookup(streamable: StreamableHTML): Set[OpenTag] = selector match {
    case ById(id) => streamable.byId.get(id).toSet
    case ByClass(className) => streamable.byClass.getOrElse(className, Set.empty)
    case ByTag(tagName) => streamable.byTag.getOrElse(tagName, Set.empty)
  }
}