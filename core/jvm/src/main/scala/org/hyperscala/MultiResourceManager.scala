//package org.hyperscala
//
//import io.undertow.server.handlers.resource.{Resource, ResourceChangeListener, ResourceManager}
//
//class MultiResourceManager(managers: ResourceManager*) extends ResourceManager {
//  override def removeResourceChangeListener(listener: ResourceChangeListener): Unit = {
//    managers.foreach(_.removeResourceChangeListener(listener))
//  }
//
//  override def registerResourceChangeListener(listener: ResourceChangeListener): Unit = {
//    managers.foreach(_.registerResourceChangeListener(listener))
//  }
//
//  override def isResourceChangeListenerSupported: Boolean = {
//    managers.exists(_.isResourceChangeListenerSupported)
//  }
//
//  override def getResource(path: String): Resource = {
//    managers.toStream.flatMap(m => Option(m.getResource(path))).headOption.orNull
//  }
//
//  override def close(): Unit = managers.foreach(_.close())
//}