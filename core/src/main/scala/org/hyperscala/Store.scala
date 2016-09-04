package org.hyperscala

trait Store {
  def apply[T](key: String): T

  def get[T](key: String): Option[T]

  def update[T](key: String, value: T): Unit

  def remove(key: String): Unit

  def getOrElse[T](key: String, default: => T): T = get[T](key).getOrElse(default)

  def getOrSet[T](key: String, default: => T): T = synchronized {
    get[T](key) match {
      case Some(value) => value
      case None => {
        val value: T = default
        update(key, value)
        value
      }
    }
  }
}

abstract class MapStore extends Store {
  def map: Map[String, Any]

  override def apply[T](key: String): T = map(key).asInstanceOf[T]

  override def get[T](key: String): Option[T] = map.get(key).asInstanceOf[Option[T]]
}

class ThreadLocalStore extends MapStore {
  private val threadLocal = new ThreadLocal[Map[String, Any]]

  def inScope: Boolean = Option(threadLocal.get()).isDefined

  override def map: Map[String, Any] = Option(threadLocal.get()).getOrElse(throw new RuntimeException("Not in a request scope."))

  override def update[T](key: String, value: T): Unit = threadLocal.set(map + (key -> value))

  override def remove(key: String): Unit = threadLocal.set(map - key)

  def clear(): Unit = threadLocal.remove()

  def scoped[R](map: Map[String, Any])(f: => R): R = {
    threadLocal.set(map)
    try {
      f
    } finally {
      clear()
    }
  }
}