package ai.metarank.model

import ai.metarank.model.Key.{Env, FeatureName, Tag}
import io.circe.{Codec, Decoder, Encoder, Json, JsonObject}
import io.circe.generic.semiauto._

case class Key(tag: Tag, name: FeatureName, env: Env)

object Key {
  case class Env(value: String)
  case class FeatureName(value: String)
  case class Tag(scope: Scope, value: String)
  case class Scope(name: String)

  implicit val tagCodec: Codec[Tag]          = deriveCodec[Tag]
  implicit val scopeCodec: Codec[Scope]      = stringCodec(_.name, Scope.apply)
  implicit val nameCodec: Codec[FeatureName] = stringCodec(_.value, FeatureName.apply)
  implicit val envCodec: Codec[Env]          = stringCodec(_.value, Env.apply)

  implicit val keyEncoder: Encoder[Key] = Encoder.instance(key =>
    Json.fromJsonObject(
      JsonObject.fromMap(
        Map(
          "scope"  -> scopeCodec(key.tag.scope),
          "id"     -> Json.fromString(key.tag.value),
          "name"   -> nameCodec(key.name),
          "tenant" -> envCodec(key.env)
        )
      )
    )
  )

  implicit val keyDecoder: Decoder[Key] = Decoder.instance(c =>
    for {
      scope  <- c.downField("scope").as[Scope]
      id     <- c.downField("id").as[String]
      name   <- c.downField("name").as[FeatureName]
      tenant <- c.downField("tenant").as[Env]
    } yield {
      Key(Tag(scope, id), name, tenant)
    }
  )

  implicit val keyCodec: Codec[Key] = Codec.from(keyDecoder, keyEncoder)

  def stringCodec[T](toString: T => String, fromString: String => T) =
    Codec.from(Decoder.decodeString.map(fromString), Encoder.encodeString.contramap(toString))

}
