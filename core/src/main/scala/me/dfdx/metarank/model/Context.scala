package me.dfdx.metarank.model

import io.circe.generic.semiauto._

case class Context(query: Option[String], tag: Option[String])

object Context {
  implicit val contextCodec = deriveCodec[Context]
}
