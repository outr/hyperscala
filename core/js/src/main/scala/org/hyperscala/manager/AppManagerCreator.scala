package org.hyperscala.manager

import org.hyperscala.WebApplication

object AppManagerCreator {
  def create(app: WebApplication): ApplicationManager = new ClientApplicationManager(app)
}