package org.hyperscala

trait Picklers {
  protected[hyperscala] def add[T](pickler: Pickler[T]): Int
}