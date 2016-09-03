package org.hyperscala

case class Param(values: List[String]) {
  lazy val value = values.mkString(", ")
}
