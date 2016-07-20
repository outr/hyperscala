package org.hyperscala

import pl.metastack.metarx.Sub

trait ClientWebApplication extends WebApplication {
  lazy val screen: Sub[Option[Screen]] = Sub[Option[Screen]](None)
}