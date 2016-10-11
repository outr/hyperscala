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
  def cancelled: StateChannel[Boolean] = request.cancelled

  private[ajax] def start(manager: AjaxManager): Unit = {
    _state := ActionState.Running
    future.onComplete { result =>
      _state := ActionState.Finished
      manager.remove(this)
    }
    request.send()
  }

  // TODO: dequeue if not already running
  def cancel(): Unit = request.cancel()     // TODO: does cancel fire onComplete with a failure?
}