package org.hyperscala

sealed trait SiteType

object SiteType {
  /**
    * A single-page site uses HTML5 history to push and replace states in the browser without doing a new page request.
    * This is by far the most efficient and generally preferred type of application, but it does require better intra-
    * page consistency since all content for all pages could be loaded into a single web page.
    */
  case object SinglePage extends SiteType

  /**
    * A multi-page site works in the more classical model of a single page per request/response. This is less efficient
    * but much easier to configure and support.
    */
  case object MultiPage extends SiteType
}