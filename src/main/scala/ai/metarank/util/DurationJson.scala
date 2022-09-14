package ai.metarank.util

import io.circe.{Decoder, Encoder, Json}

import scala.concurrent.duration.{DAYS, FiniteDuration, HOURS, MINUTES, SECONDS}
import scala.util.{Failure, Try}

object DurationJson {
  val durationFormat = "([0-9]+)([smhd]{1})".r
  implicit val durationDecoder: Decoder[FiniteDuration] = Decoder.decodeString.emapTry {
    case durationFormat(num, suffix) => Try(FiniteDuration(num.toLong, suffix))
    case d                           => Failure(new IllegalArgumentException(s"duration is in wrong format: $d"))
  }

  implicit val durationEncoder: Encoder[FiniteDuration] = Encoder.instance(d => {
    val encoded = d.unit match {
      case SECONDS => s"${d.length}s"
      case MINUTES => s"${d.length}m"
      case HOURS   => s"${d.length}h"
      case DAYS    => s"${d.length}d"
      case _       => s"${d.toSeconds}s"
    }
    Json.fromString(encoded)
  })

}
