package org

import scala.language.implicitConversions

package object hyperscala {
  implicit def screen2ServerScreen(screen: BaseScreen): ServerScreen = screen.asInstanceOf[ServerScreen]
}