package org.hyperscala

import java.io.{File, IOException}
import java.nio.file.Path
import java.util
import java.util.Date
import java.util.concurrent.TimeUnit

import io.undertow.UndertowLogger
import io.undertow.io.IoCallback
import io.undertow.server.handlers.ResponseCodeHandler
import io.undertow.server.handlers.cache.ResponseCache
import io.undertow.server.handlers.resource.{ClassPathResourceManager, DirectoryUtils, FileResource, FileResourceManager, RangeAwareResource, Resource, ResourceChangeEvent, ResourceChangeListener, ResourceHandler, ResourceManager, URLResource}
import io.undertow.server.{HttpHandler, HttpServerExchange}
import io.undertow.util.{ByteRange, CanonicalPathUtils, DateUtils, ETagUtils, Headers, Methods, StatusCodes}
import org.xnio.{FileChangeCallback, FileChangeEvent, OptionMap, Xnio}

import scala.collection.JavaConversions._

class FunctionalResourceManager extends ResourceManager {
  private var listeners = Set.empty[ResourceChangeListener]
  private var mappings = Set.empty[ResourceMapping]

  private def defaultFileConversion(directory: File) = (path: String) => {
    val f = new File(directory, path)
    if (f.exists()) {
      Some(f)
    } else {
      None
    }
  }

  override def removeResourceChangeListener(listener: ResourceChangeListener): Unit = synchronized {
    listeners -= listener
  }

  override def registerResourceChangeListener(listener: ResourceChangeListener): Unit = synchronized {
    listeners += listener
  }

  override def isResourceChangeListenerSupported: Boolean = true

  override def getResource(path: String): Resource = mappings.toStream.flatMap { m =>
    m.lookup(path)
  }.headOption.orNull

  def fileMapping(directory: File)(pathConversion: String => Option[File] = defaultFileConversion(directory)): Unit = {
    this += new PathResourceMapping {
      override def base: String = directory.getCanonicalPath

      override def lookup(path: String): Option[Resource] = pathConversion(path).map(f => new FileResource(f, fileResourceManager, f.getCanonicalPath.substring(base.length)))
    }
  }

  def filePathMapping(directory: File)(pathConversion: String => Option[String] = (s: String) => Some(s)): Unit = {
    this += new PathResourceMapping {
      override def base: String = directory.getCanonicalPath

      override def lookup(path: String): Option[Resource] = pathConversion(path).flatMap { updated =>
        val file = new File(directory, updated)
        if (file.exists()) {
          Some(new FileResource(file, fileResourceManager, updated))
        } else {
          None
        }
      }
    }
  }

  def pathMapping(path: Path)(pathConversion: String => Option[String] = (s: String) => Some(s)): Unit = {
    this += new PathResourceMapping {
      override def base: String = path.toAbsolutePath.toString

      override def lookup(path: String): Option[Resource] = pathConversion(path).flatMap { updated =>
        val file = new File(base, updated)
        if (file.exists()) {
          Some(new FileResource(file, fileResourceManager, updated))
        } else {
          None
        }
      }
    }
  }

  def classPathMapping(path: String)(pathConversion: String => Option[String] = (s: String) => Some(s)): Unit = {
    this += new ClassPathResourceMapping {
      override def base: String = path match {
        case _ if path.endsWith("/") => path.substring(0, path.length - 1)
        case _ => path
      }

      override def lookup(path: String): Option[Resource] = pathConversion(path).flatMap { updated =>
        val combined = s"$base$updated" match {
          case c if c.startsWith("/") => c.substring(1)
          case c => c
        }
        Option(getClass.getClassLoader.getResource(combined)).map(url => new URLResource(url, url.openConnection(), updated))
      }
    }
  }

  def +=(mapping: ResourceMapping): Unit = synchronized {
    mappings += mapping
  }

  override def close(): Unit = {}

  protected[hyperscala] def fire(events: java.util.Collection[ResourceChangeEvent]): Unit = {
    listeners.foreach(_.handleChanges(events))
  }
}

trait ResourceMapping {
  def init(resourceManager: FunctionalResourceManager): Unit = {}

  def lookup(path: String): Option[Resource]
}

trait ClassPathResourceMapping extends ResourceMapping {
  protected lazy val classPathResourceManager = new ClassPathResourceManager(getClass.getClassLoader)

  def base: String
}

trait PathResourceMapping extends ResourceMapping {
  private lazy val fileSystemWatcher = Xnio.getInstance().createFileSystemWatcher(s"Watcher for $base", OptionMap.EMPTY)
  protected lazy val fileResourceManager = new FileResourceManager(new File(base), 100L)

  def base: String

  override def init(resourceManager: FunctionalResourceManager): Unit = {
    super.init(resourceManager)

    fileSystemWatcher.watchPath(new File(base), new FileChangeCallback {
      override def handleChanges(changes: util.Collection[FileChangeEvent]): Unit = resourceManager.synchronized {
        val events = changes.collect {
          case change if change.getFile.getAbsolutePath.startsWith(base) => {
            val path = change.getFile.getAbsolutePath.substring(base.length)
            new ResourceChangeEvent(path, ResourceChangeEvent.Type.valueOf(change.getType.name()))
          }
        }
        if (events.nonEmpty) {
          resourceManager.fire(events)
        }
      }
    })
  }
}

class FunctionalResourceHandler(resourceManager: FunctionalResourceManager) extends ResourceHandler(resourceManager) {
  setDirectoryListingEnabled(false)

  override def handleRequest(exchange: HttpServerExchange): Unit = exchange.getRequestMethod match {
    case Methods.GET | Methods.POST => serveResource(exchange, sendContent = true)
    case Methods.HEAD => serveResource(exchange, sendContent = false)
    case _ => {
      exchange.setStatusCode(StatusCodes.METHOD_NOT_ALLOWED)
      exchange.endExchange()
    }
  }

  private def serveResource(exchange: HttpServerExchange, sendContent: Boolean): Unit = {
    if (DirectoryUtils.sendRequestedBlobs(exchange)) {
      // Support for directory listing (possible future usage)
    } else if (!getAllowed.resolve(exchange)) {   // TODO: support forbidden in functional
      exchange.setStatusCode(StatusCodes.FORBIDDEN)
      exchange.endExchange()
    } else {
      val cache = exchange.getAttachment(ResponseCache.ATTACHMENT_KEY)
      val cachable = getCachable.resolve(exchange)

      if (cachable && getCacheTime != null) {
        exchange.getResponseHeaders.put(Headers.CACHE_CONTROL, s"public, max-age=$getCacheTime")
        val date = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(getCacheTime.toLong)
        val dateHeader = DateUtils.toDateString(new Date(date))
        exchange.getResponseHeaders.put(Headers.EXPIRES, dateHeader)
      }
      if (cache != null && cachable && cache.tryServeResponse()) {
        // Cached response
      } else {
        val serverSession = Server.session.undertowSession
        val dispatchTask = new HttpHandler {
          override def handleRequest(exchange: HttpServerExchange): Unit = try {
            Server.withServerSession(serverSession) {
              val resourceOption: Option[Resource] = if ((File.separatorChar == '/' || !exchange.getRelativePath.contains(File.separator)) && isCanonicalizePaths) {
                Option(resourceManager.getResource(CanonicalPathUtils.canonicalize(exchange.getRelativePath)))
              } else {
                Option(resourceManager.getResource(exchange.getRelativePath))
              }
              resourceOption match {
                case Some(resource) => {
                  if (resource.isDirectory) {
                    throw new RuntimeException(s"Directories not currently supported: ${exchange.getRelativePath}")
                  } else if (exchange.getRelativePath.endsWith("/")) {
                    exchange.setStatusCode(StatusCodes.NOT_FOUND)
                    exchange.endExchange()
                  } else {
                    val etag = resource.getETag
                    val lastModified = resource.getLastModified
                    if (!ETagUtils.handleIfMatch(exchange, etag, false) || !DateUtils.handleIfUnmodifiedSince(exchange, lastModified)) {
                      exchange.setStatusCode(StatusCodes.NOT_MODIFIED)
                      exchange.endExchange()
                    } else {
                      val contentEncodedResourceManager = getContentEncodedResourceManager
                      val contentLength = resource.getContentLength
                      if (contentLength != null && !exchange.getResponseHeaders.contains(Headers.TRANSFER_ENCODING)) {
                        exchange.setResponseContentLength(contentLength)
                      }
                      val rangeResponse = resource match {
                        case rar: RangeAwareResource if rar.isRangeSupported && contentLength != null && contentEncodedResourceManager == null => {
                          exchange.getResponseHeaders.put(Headers.ACCEPT_RANGES, "bytes")
                          val range = ByteRange.parse(exchange.getRequestHeaders.getFirst(Headers.RANGE))
                          if (range != null && range.getRanges == 1 && resource.getContentLength != null) {
                            Option(range.getResponseResult(resource.getContentLength, exchange.getRequestHeaders.getFirst(Headers.IF_RANGE), resource.getLastModified, Option(resource.getETag).map(_.getTag).orNull))
                          } else {
                            None
                          }
                        }
                        case _ => None
                      }
                      val (start, end) = rangeResponse.map { rr =>
                        exchange.setStatusCode(rr.getStatusCode)
                        exchange.getResponseHeaders.put(Headers.CONTENT_RANGE, rr.getContentRange)
                        exchange.setResponseContentLength(rr.getContentLength)
                        (rr.getStart, rr.getEnd)
                      }.getOrElse((-1L, -1L))
                      if (exchange.getStatusCode != StatusCodes.REQUEST_RANGE_NOT_SATISFIABLE) {
                        if (!exchange.getResponseHeaders.contains(Headers.CONTENT_TYPE)) {
                          val contentType = Option(resource.getContentType(getMimeMappings)).getOrElse("application/octet/stream")
                          exchange.getResponseHeaders.put(Headers.CONTENT_TYPE, contentType)
                        }
                        if (lastModified != null) {
                          exchange.getResponseHeaders.put(Headers.LAST_MODIFIED, resource.getLastModifiedString)
                        }
                        if (etag != null) {
                          exchange.getResponseHeaders.put(Headers.ETAG, etag.toString)
                        }
                        Option(contentEncodedResourceManager).map(_.getResource(resource, exchange)) match {
                          case Some(encoded) => {
                            exchange.getResponseHeaders.put(Headers.CONTENT_ENCODING, encoded.getContentEncoding)
                            exchange.getResponseHeaders.put(Headers.CONTENT_LENGTH, encoded.getResource.getContentLength)
                            encoded.getResource.serve(exchange.getResponseSender, exchange, IoCallback.END_EXCHANGE)
                          }
                          case None if !sendContent => exchange.endExchange()
                          case None => rangeResponse match {
                            case Some(rr) => resource.asInstanceOf[RangeAwareResource].serveRange(exchange.getResponseSender, exchange, start, end, IoCallback.END_EXCHANGE)
                            case None => resource.serve(exchange.getResponseSender, exchange, IoCallback.END_EXCHANGE)
                          }
                        }
                      }
                    }
                  }
                }
                case None => ResponseCodeHandler.HANDLE_404.handleRequest(exchange)
              }
            }
          } catch {
            case exc: IOException => {
              UndertowLogger.REQUEST_IO_LOGGER.ioException(exc)
              exchange.setStatusCode(StatusCodes.INTERNAL_SERVER_ERROR)
              exchange.endExchange()
            }
          }
        }
        if (exchange.isInIoThread) {
          exchange.dispatch(dispatchTask)
        } else {
          dispatchTask.handleRequest(exchange)
        }
      }
    }
  }
}