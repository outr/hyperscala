package org.hyperscala

import com.outr.scribe.Logging

trait ConnectionManager extends Logging {
  def connections: Set[Connection]
  def connectionOption: Option[Connection]
  def connection: Connection = connectionOption.getOrElse(throw new RuntimeException("No connection defined."))
}