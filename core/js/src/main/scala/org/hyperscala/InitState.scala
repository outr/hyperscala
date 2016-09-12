package org.hyperscala

sealed trait InitState

object InitState {
  case object PageLoad extends InitState
  case object ScreenLoad extends InitState
  case object ScreenReload extends InitState
}