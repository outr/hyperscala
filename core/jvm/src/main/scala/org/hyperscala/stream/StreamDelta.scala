package org.hyperscala.stream

import org.hyperscala.delta._

class StreamDelta(delta: Delta) {
  def apply(streamer: HTMLStream, tag: OpenTag): Unit = delta match {
    case Replace(selector, content) => streamer.replace(tag.start, tag.close.map(_.end).getOrElse(tag.end), content())
    case ReplaceAttribute(selector, attributeName, content) => {
      streamer.process(tag.start, tag.end, (block: String) => {
        s"""$attributeName="(.*?)"""".r.replaceAllIn(block, replacer => {
          s"""$attributeName="${content()}""""
        })
      })
    }
    case Processor(selector, replace, onlyOpenTag, processor) => {
      val end = if (onlyOpenTag) {
        tag.end
      } else {
        tag.close.map(_.end).getOrElse(tag.end)
      }
      streamer.process(tag.start, end, processor, replace = replace)
    }
    case InsertBefore(selector, content) => streamer.insert(tag.start, content())
    case InsertFirstChild(selector, content) => streamer.insert(tag.end, content())
    case ReplaceContent(selector, content) => streamer.replace(tag.end, tag.close.get.start, content())
    case InsertLastChild(selector, content) => streamer.insert(tag.close.get.start, content())
    case InsertAfter(selector, content) => streamer.insert(tag.close.map(_.end).getOrElse(tag.end), content())
    case Template(selector, deltas) => streamer.insert(tag.start, streamer.streamable.stream(deltas, Some(selector)))
  }
}