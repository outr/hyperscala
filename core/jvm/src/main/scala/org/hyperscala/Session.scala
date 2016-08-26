package org.hyperscala

import java.util.UUID

trait Session

trait SessionManager[S <: Session] {
  def apply(): S = store.getOrSet[S](sessionKey, create())

  protected def sessionKey: String
  protected def store: Store
  protected def create(): S
}

trait RequestSessionManager[S <: Session] extends SessionManager[S] {
  override protected def store: Store = Server.request
}

trait DefaultSessionManager[S <: Session] extends SessionManager[S] {
  override protected val sessionKey: String = UUID.randomUUID().toString

  override protected def store: Store = Server.session
}