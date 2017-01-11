package org.hyperscala

case class URL(protocol: Protocol = Protocol.Http,
               host: String = "localhost",
               port: Int = 80,
               path: String = "/",
               parameters: Map[String, Param] = Map.empty) {
  def replacePathAndParams(pathAndParams: String): URL = {
    val b = new StringBuilder
    b.append(protocol.scheme)
    b.append("://")
    b.append(host)
    protocol match {
      case Protocol.Http if port == 80 => // No need
      case Protocol.Https if port == 443 => // No need
      case _ => b.append(s":$port")
    }
    b.append(pathAndParams)
    URL(b.toString())
  }
  def withParam(key: String, value: String, replace: Boolean = true): URL = {
    val params: Map[String, Param] = if (replace) {
      if (value.nonEmpty) {
        parameters + (key -> Param(List(value)))
      } else {
        parameters - key
      }
    } else {
      if (value.nonEmpty) {
        parameters + (key -> Param(value :: parameters.get(key).map(_.values).getOrElse(Nil)))
      } else {
        parameters
      }
    }
    copy(parameters = params)
  }

  def param(key: String): Option[String] = parameters.get(key).map(_.value)

  def withBase(base: String): URL = {
    var s = base
    var protocol = this.protocol
    var host = this.host
    var port = this.port
    val protocolBreak = s.indexOf("://")
    if (protocolBreak != -1) {
      protocol = Protocol(s.substring(0, protocolBreak))
      s = s.substring(protocolBreak + 3)
    }
    val portBreak = s.indexOf(':')
    if (portBreak != -1) {
      port = s.substring(portBreak + 1).toInt
      s = s.substring(0, portBreak)
    } else if (protocol == Protocol.Http) {
      port = 80
    } else if (protocol == Protocol.Https) {
      port = 443
    }
    if (s.nonEmpty) {
      host = s
    }
    copy(protocol = protocol, host = host, port = port)
  }

  lazy val base: String = {
    val b = new StringBuilder
    b.append(protocol.scheme)
    b.append("://")
    b.append(host)
    protocol match {
      case Protocol.Http if port == 80 => // No need
      case Protocol.Https if port == 443 => // No need
      case _ => b.append(s":$port")
    }
    b.toString()
  }

  lazy val pathAndArgs: String = {
    val b = new StringBuilder
    b.append(Util.encodePath(path))
    if (parameters.nonEmpty) {
      b.append('?')
      val params = parameters.flatMap {
        case (key, param) => {
          val keyEncoded = Util.encodeURIComponent(key)
          param.values.map { value =>
            val valueEncoded = Util.encodeURIComponent(value)
            s"$keyEncoded=$valueEncoded"
          }
        }
      }.mkString("&")
      b.append(params)
    }
    b.toString()
  }

  lazy val asString: String = s"$base$pathAndArgs"

  override def toString: String = asString
}

object URL {
  def apply(url: String): URL = if (url.indexOf('#') != -1) {
    apply(url.substring(0, url.indexOf('#')))     // Clip hash as it's not supported server-side
  } else {
    val colonIndex1 = url.indexOf(':')
    val protocol = Protocol(url.substring(0, colonIndex1))
    val slashIndex = url.indexOf('/', colonIndex1 + 3)
    val hostAndPort = if (slashIndex == -1) {
      url.substring(colonIndex1 + 3)
    } else {
      url.substring(colonIndex1 + 3, slashIndex)
    }
    val colonIndex2 = hostAndPort.indexOf(':')
    val (host, port) = if (colonIndex2 == -1) {
      hostAndPort -> (protocol match {
        case Protocol.Http => 80
        case Protocol.Https => 443
        case _ => throw new RuntimeException(s"Unknown port for $url.")
      })
    } else {
      hostAndPort.substring(0, colonIndex2) -> hostAndPort.substring(colonIndex2 + 1).toInt
    }
    val questionIndex = url.indexOf('?')
    val path = if (slashIndex == -1) {
      "/"
    } else if (questionIndex == -1) {
      Util.decodePath(url.substring(slashIndex))
    } else {
      Util.decodePath(url.substring(slashIndex, questionIndex))
    }
    val parameters = if (questionIndex == -1) {
      Map.empty[String, Param]
    } else {
      val query = url.substring(questionIndex + 1)
      var map = Map.empty[String, Param]
      query.split('&').map(param => param.trim.splitAt(param.indexOf('='))).collect {
        case (key, value) if key.nonEmpty => Util.decodeURIComponent(key) -> Util.decodeURIComponent(value.substring(1))
        case (key, value) if value.nonEmpty => "query" -> Util.decodeURIComponent(value)
      }.foreach {
        case (key, value) => map.get(key) match {
          case Some(param) => map += key -> Param(param.values ::: List(value))
          case None => map += key -> Param(List(value))
        }
      }
      map
    }
    URL(protocol = protocol, host = host, port = port, path = path, parameters = parameters)
  }
}