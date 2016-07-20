package org.hyperscala.stream

import java.io.{File, FileInputStream, InputStream}

import scala.annotation.tailrec
import scala.collection.mutable

object HTMLParser {
  private val SelfClosingTagRegex = """<(\S+)(.*)/>""".r
  private val OpenTagRegex = """<(\S+)(.*)>""".r
  private val CloseTagRegex = """</(\S+).*>""".r

  def main(args: Array[String]): Unit = {
    val file = new File("simple.html")
    val streamable = apply(file)

    case class ItemData(name: String, value: String)

    val data = List(
      ItemData("First", "This is the first value."),
      ItemData("Second", "This is the second value."),
      ItemData("Third", "This is the third value.")
    )

    val deltas = List(
      Delta.ReplaceContent(ByTag("title"), "Modified Title"),
      Delta.Replace(ById("head"), "<h1>Heading</h1>"),
      Delta.InsertLastChild(ById("body"), "<b>Last Entry</b>"),
      Delta.InsertAfter(ById("footer"), "<h5>Copyright (c)</h5>"),
      Delta.ReplaceContent(ById("footer"), "The updated footer"),
      Delta.Repeat(ByClass("item"), data, (d: ItemData) => List(
        Delta.ReplaceContent(ByClass("itemName"), d.name),
        Delta.ReplaceContent(ByClass("itemValue"), d.value)
      ))
    )
    val html = streamable.stream(deltas, Some(ById("body")))
    println(html)
  }

  def apply(file: File): StreamableHTML = {
    val lastModified = file.lastModified()
    val input = new FileInputStream(file)
    try {
      val parser = new HTMLParser(input)
      val tags = parser.parse()
      var byId = Map.empty[String, OpenTag]
      var byClass = Map.empty[String, Set[OpenTag]]
      var byTag = Map.empty[String, Set[OpenTag]]
      tags.foreach { tag =>
        if (tag.attributes.contains("id")) {
          byId += tag.attributes("id") -> tag
        }
        tag.attributes.getOrElse("class", "").split(" ").foreach { className =>
          val cn = className.trim
          if (cn.nonEmpty) {
            var classTags = byClass.getOrElse(cn, Set.empty[OpenTag])
            classTags += tag
            byClass += cn -> classTags
          }
        }
        var tagsByName = byTag.getOrElse(tag.tagName, Set.empty[OpenTag])
        tagsByName += tag
        byTag += tag.tagName -> tagsByName
      }
      new StreamableHTML(file, file.lastModified() != lastModified, byId, byClass, byTag)
    } finally {
      input.close()
    }
  }
}

class HTMLParser(input: InputStream) {
  import HTMLParser._

  private var position = 0
  private val b = new StringBuilder
  private var quotes = false
  private var tagStart = -1
  private var tagEnd = -1

  private val open = new mutable.Stack[OpenTag]
  private var tags = Set.empty[OpenTag]

  @tailrec
  final def parse(): Set[OpenTag] = input.read() match {
    case -1 => tags   // Finished
    case i => {
      val c = i.toChar
      process(c)
      parse()
    }
  }

  private def process(c: Char): Unit = {
    b.append(c)
    if (c == '"') {
      if (quotes) {
        quotes = false
      } else {
        quotes = true
      }
    } else if (c == '\n' || c == '\r') {
      quotes = false
    } else if (c == '<') {
      b.clear()
      b.append(c)
      tagStart = position
    } else if (c == '>') {
      tagEnd = position + 1
      parseTag()
      b.clear()
    }

    position += 1
  }

  private def parseTag(): Unit = b.toString() match {
    case s if s.startsWith("<!--") || s.endsWith("-->") => // Ignore
    case CloseTagRegex(tagName) => {
      val closeTag = CloseTag(tagName, tagStart, tagEnd)
      val openTag = closeUntil(tagName).copy(close = Some(closeTag))
      tags += openTag
    }
    case SelfClosingTagRegex(tagName, attributes) => {
      val a = parseAttributes(attributes)
      val tag = OpenTag(tagName, a, tagStart, tagEnd, close = None)
      tags += tag
    }
    case OpenTagRegex(tagName, attributes) => {
      val a = parseAttributes(attributes)
      val tag = OpenTag(tagName, a, tagStart, tagEnd, close = None)
      open.push(tag)
    }
  }

  private val validAttributes = Set("id", "class")

  private def parseAttributes(attributes: String): Map[String, String] = {
    val sb = new StringBuilder
    var quoted = false
    var key = ""
    var map = Map.empty[String, String]
    attributes.foreach { c =>
      if (c == '"') {
        if (quoted) {
          map += key -> sb.toString()
          quoted = false
          sb.clear()
        } else {
          quoted = true
        }
      } else if ((c == '=' || c == ' ') && !quoted) {
        if (sb.nonEmpty) {
          key = sb.toString()
          sb.clear()
        }
      } else {
        sb.append(c)
      }
    }
    map.filter(t => validAttributes.contains(t._1))
  }

  @tailrec
  private def closeUntil(tagName: String): OpenTag = {
    val t = open.pop()
    if (t.tagName.equalsIgnoreCase(tagName)) {
      t
    } else {
      tags += t
      closeUntil(tagName)
    }
  }
}