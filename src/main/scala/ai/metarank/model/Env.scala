package ai.metarank.model

import io.circe.{Codec, Decoder, Encoder}

case class Env(value: String)

object Env {
  val default = Env("default")
  implicit val envEncoder: Encoder[Env] = Encoder.encodeString.contramap(_.value)
  implicit val envDecoder: Decoder[Env] = Decoder.decodeString.map(Env.apply)
  implicit val envCodec: Codec[Env]     = Codec.from(envDecoder, envEncoder)
}
