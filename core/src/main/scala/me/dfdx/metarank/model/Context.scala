package me.dfdx.metarank.model

import io.circe.Codec
import io.circe.generic.semiauto._

case class Context(query: Option[String], tag: Option[String])

object Context {
  implicit val contextCodec = Codec.from(
    decodeA = deriveDecoder[Context]
      .ensure(c => c.query.isDefined || c.tag.isDefined, "context should have either tag or query defined"),
    encodeA = deriveEncoder[Context]
  )
}
