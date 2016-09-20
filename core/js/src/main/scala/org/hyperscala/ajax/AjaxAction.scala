package org.hyperscala.ajax

import pl.metastack.metarx.{StateChannel, Var}

import scala.concurrent.ExecutionContext.Implicits.global

class AjaxAction(request: AjaxRequest) {
  lazy val future = request.promise.future
  private[ajax] val _state = Var[ActionState](ActionState.New)
  def state: StateChannel[ActionState] = _state
  def loaded: StateChannel[Int] = request.loaded
  def total: StateChannel[Int] = request.total
  def percentage: StateChannel[Int] = request.percentage

  private[ajax] def start(manager: AjaxManager): Unit = {
    _state := ActionState.Running
    future.onComplete { result =>
      _state := ActionState.Finished
      manager.remove(this)
    }
  }
}