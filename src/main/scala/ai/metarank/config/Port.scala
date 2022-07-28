package ai.metarank.config

import io.circe.{Codec, Decoder, Encoder}

import scala.util.{Failure, Success}

case class Port(value: Int) extends AnyVal

object Port {
  implicit val portDecoder: Decoder[Port] = Decoder.decodeInt.emapTry {
    case port if port > 0 && port < 65536 => Success(Port(port))
    case other                            => Failure(ConfigParsingError(s"port $other should be in 0..65536 range"))
  }

}
