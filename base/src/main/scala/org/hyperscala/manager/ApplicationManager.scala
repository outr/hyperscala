package org.hyperscala.manager

import com.outr.scribe.Logging
import org.hyperscala.Connection

trait ApplicationManager extends Logging {
  def connections: Set[Connection]
  def connectionOption: Option[Connection]
  def connection: Connection = connectionOption.getOrElse(throw new RuntimeException("No connection defined."))
  def init(): Unit
}
