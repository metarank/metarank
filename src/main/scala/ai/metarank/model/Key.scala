package ai.metarank.model

import ai.metarank.fstore.codec.impl.ScopeCodec
import ai.metarank.model.Key.FeatureName
import io.circe.{Codec, Decoder, Encoder, Json, JsonObject}

case class Key(scope: Scope, feature: FeatureName) {
  override val hashCode: Int = scope.hashCode() ^ feature.value.hashCode
  def encode                 = s"${scope.asString}/${feature.value}"
}

object Key {

  def fromString(str: String): Either[Throwable, Key] = {
    val slashIndex = str.indexOf('/'.toInt)
    if (slashIndex > 0) {
      val scopeString = str.substring(0, slashIndex)
      val feature     = str.substring(slashIndex + 1)
      ScopeCodec.decode(scopeString).map(s => Key(s, FeatureName(feature)))
    } else {
      Left(new IllegalArgumentException("slash not found"))
    }
  }

  case class FeatureName(value: String) extends AnyVal {
    override def toString = value
  }

  given nameCodec: Codec[FeatureName] = stringCodec(_.value, FeatureName.apply)

  given keyEncoder: Encoder[Key] = Encoder.instance(key =>
    Json.fromJsonObject(
      JsonObject.fromMap(
        Map(
          "scope"   -> Scope.scopeEncoder(key.scope),
          "feature" -> nameCodec(key.feature)
        )
      )
    )
  )

  given keyDecoder: Decoder[Key] = Decoder.instance(c =>
    for {
      scope   <- c.downField("scope").as[Scope]
      feature <- c.downField("feature").as[FeatureName]
    } yield {
      Key(scope, feature)
    }
  )

  given keyCodec: Codec[Key] = Codec.from(keyDecoder, keyEncoder)

  def stringCodec[T](toString: T => String, fromString: String => T) =
    Codec.from(Decoder.decodeString.map(fromString), Encoder.encodeString.contramap(toString))

}
