package org.hyperscala.stream

import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption

import com.outr.scribe.Logging

class StreamableHTML(file: File,
                     isStale: => Boolean,
                     val byId: Map[String, OpenTag],
                     val byClass: Map[String, Set[OpenTag]],
                     val byTag: Map[String, Set[OpenTag]]) extends Logging {
  def stream(deltas: List[Delta], selector: Option[Selector] = None, includeTag: Boolean = true): String = {
    val channel = FileChannel.open(file.toPath, StandardOpenOption.READ)
    try {
      val streamer = new HTMLStream(this)
      val tag = selector.flatMap(_.lookup(this).headOption)
      val start = tag.map { t =>
        if (includeTag) {
          t.start
        } else {
          t.end
        }
      }
      val end = tag.map { t =>
        if (includeTag) {
          t.close.get.end
        } else {
          t.close.get.start
        }
      }.getOrElse(file.length().toInt)
      deltas.foreach { delta =>
        val tags = delta.selector.lookup(this)
        tags.foreach { tag =>
          if (tag.start >= start.getOrElse(0) && tag.close.map(_.end).getOrElse(tag.end) <= end) {
            delta(streamer, tag)
          } else {
            logger.debug(s"Excluding $tag")
          }
        }
      }
      streamer.stream(channel, end, start)
    } finally {
      channel.close()
    }
  }
}