/** Copyright 2012-present Snowplow Analytics Ltd
  *
  * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
  * the License. You may obtain a copy of the License at
  *
  * http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
  * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
  * specific language governing permissions and limitations under the License.
  *
  * Vendored from https://github.com/snowplow-referer-parser/scala-referer-parser (Parser.scala, Referer.scala,
  * RefererLookup.scala, ParseReferers.scala), consolidated into a single file with the CreateParser typeclass replaced
  * by RefererParser.fromString.
  */
package ai.metarank.util

import ai.metarank.util.RefererParser.*
import cats.implicits.*
import io.circe.{ACursor, Decoder, Json}

import java.net.{URI, URLDecoder}

class RefererParser(referers: Map[String, RefererLookup]) {

  private def toUri(uri: String): Option[URI] =
    if (uri == "")
      None
    else
      Either.catchNonFatal(new URI(uri)).toOption

  def parse(refererUri: URI): Option[Referer] =
    parse(refererUri, None, Nil)

  def parse(refererUri: String): Option[Referer] =
    toUri(refererUri).flatMap(uri => parse(uri, None, Nil))

  def parse(refererUri: URI, pageHost: String): Option[Referer] =
    parse(refererUri, Some(pageHost), Nil)

  def parse(refererUri: String, pageHost: String): Option[Referer] =
    toUri(refererUri).flatMap(uri => parse(uri, Some(pageHost), Nil))

  def parse(refererUri: URI, pageUri: URI): Option[Referer] =
    parse(refererUri, Some(pageUri.getHost), Nil)

  def parse(refererUri: String, pageUri: URI): Option[Referer] =
    toUri(refererUri).flatMap(uri => parse(uri, Some(pageUri.getHost), Nil))

  /** Parses a `refererUri` URI to return either Some Referer, or None. */
  def parse(
      refererUri: URI,
      pageHost: Option[String],
      internalDomains: List[String]
  ): Option[Referer] = {
    val scheme = refererUri.getScheme
    val host   = refererUri.getHost
    val path   = refererUri.getPath
    val query  = Option(refererUri.getRawQuery)

    val validSchemes = Seq("http", "https", "android-app")

    val validUri = validSchemes.contains(scheme) && host != null && path != null

    if (validUri)
      if ( // Check for internal domains
        pageHost.exists(_.equals(host)) ||
        internalDomains.map(_.trim()).contains(host)
      )
        Some(InternalReferer)
      else
        Some(
          lookupReferer(host, path)
            .map { lookup =>
              val term = query.flatMap(q => extractTerm(q, lookup.parameters))
              ExternalReferer(lookup.medium, lookup.source, term)
            }
            .getOrElse(UnknownReferer)
        )
    else
      None
  }

  private def extractTerm(query: String, possibleParameters: List[String]): Option[String] =
    extractQueryParams(query).find(p => possibleParameters.contains(p._1)).map(_._2)

  private def extractQueryParams(query: String): List[(String, String)] =
    query.split("&").toList.map { pair =>
      val equalsIndex = pair.indexOf("=")
      if (equalsIndex > 0)
        (
          decodeUriPart(pair.substring(0, equalsIndex)),
          decodeUriPart(pair.substring(equalsIndex + 1))
        )
      else
        (decodeUriPart(pair), "")
    }

  private def decodeUriPart(part: String): String = URLDecoder.decode(part, "UTF-8")

  private def lookupReferer(refererHost: String, refererPath: String): Option[RefererLookup] = {
    val hosts = hostsToTry(refererHost).to(LazyList)
    val paths = pathsToTry(refererPath).to(LazyList)

    // Since streams are lazy we don't calculate past the first element
    val results: LazyList[RefererLookup] = for {
      path   <- paths
      host   <- hosts
      result <- referers.get(host + path).to(LazyList)
    } yield result

    results.headOption
  }

  /** Splits a full hostname into possible hosts to lookup. For instance, hostsToTry("www.google.com") ==
    * List("www.google.com", "google.com", "com")
    */
  private def hostsToTry(refererHost: String): List[String] =
    refererHost
      .split("\\.")
      .toList
      .scanRight("")((part, full) => s"$part.$full")
      .init
      .map(s => s.substring(0, s.length - 1))

  /** Splits a full path into possible paths to try. Includes full path, no path and first path level. For instance,
    * pathsToTry("google.com/images/1/2/3") == List("/images/1/2/3", "/images", "")
    */
  private def pathsToTry(refererPath: String): List[String] =
    refererPath.split("/").find(_ != "") match {
      case Some(p) => List(refererPath, "/" + p, "")
      case None    => List("")
    }
}

object RefererParser {

  /** Referer - returned from parse, representing any type of referer source. Can be internal, unknown, or external with
    * a specific medium type.
    */
  sealed trait Referer

  /** Internal referer - traffic from the same domain as the page. */
  case object InternalReferer extends Referer

  /** Unknown referer - traffic from an unrecognized source. */
  case object UnknownReferer extends Referer

  /** External referer - traffic from a known external source with a specific medium. All external referers have a
    * source and may optionally have a term extracted from query parameters.
    */
  final case class ExternalReferer(
      medium: String,
      source: String,
      term: Option[String]
  ) extends Referer

  /* Hold the structure of a referer lookup */
  final case class RefererLookup(
      medium: String,
      source: String,
      parameters: List[String]
  )

  final case class CorruptJsonException(message: String) extends Exception(message)

  private final case class JsonEntry(
      domains: List[String],
      parameters: Option[List[String]]
  )

  private given jsonEntryDecoder: Decoder[JsonEntry] = Decoder.instance { c =>
    for {
      domains    <- c.get[List[String]]("domains")
      parameters <- c.get[Option[List[String]]]("parameters")
    } yield JsonEntry(domains, parameters)
  }

  def fromString(rawJson: String): Either[Exception, RefererParser] =
    io.circe.parser.parse(rawJson).flatMap(loadJson)

  def fromMap(referers: Map[String, RefererLookup]): RefererParser =
    new RefererParser(referers)

  def loadJson(doc: Json): Either[Exception, RefererParser] =
    parseReferersJson(doc.hcursor).map { parsed =>
      val lookup = parsed.foldLeft(Map.empty[String, RefererLookup]) { case (acc, (medium, entries)) =>
        entries.foldLeft(acc) { case (accInner, (source, entry)) =>
          accInner ++ entry.domains.map(_ -> RefererLookup(medium, source, entry.parameters.getOrElse(Nil)))
        }
      }
      new RefererParser(lookup)
    }

  private def parseReferersJson(
      c: ACursor
  ): Either[Exception, Map[String, Map[String, JsonEntry]]] =
    for {
      mediumKeys <- someOrExcept(c.keys, "Referers json must be an object")
      mediumEntries <-
        mediumKeys.toList
          .map(medium =>
            for {
              sourceNames <- someOrExcept(c.downField(medium).keys, s"Medium '$medium' not an object")
              sourceEntriesJson = sourceNames.map(mediumName => c.downField(medium).downField(mediumName))
              sourceEntries <- sourceEntriesJson.map(_.as[JsonEntry]).toList.sequence
            } yield medium -> sourceNames.zip(sourceEntries).toMap
          )
          .sequence
    } yield mediumEntries.toMap

  private def someOrExcept[A](opt: Option[A], message: String): Either[Exception, A] =
    opt.toRight(CorruptJsonException(message))
}
