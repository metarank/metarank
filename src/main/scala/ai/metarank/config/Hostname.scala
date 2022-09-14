package ai.metarank.config

import io.circe.{Codec, Decoder, Encoder}

import scala.util.{Failure, Success}

case class Hostname(value: String) extends AnyVal

object Hostname {
  implicit val hostnameDecoder: Decoder[Hostname] = Decoder.decodeString.emapTry {
    case ""    => Failure(ConfigParsingError("hostname cannot be empty"))
    case other => Success(Hostname(other))
  }

  implicit val hostnameEncoder: Encoder[Hostname] = Encoder.encodeString.contramap(_.value)
}
