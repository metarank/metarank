package ai.metarank.util

import io.circe.Decoder

import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Try}

object DurationJson {
  val durationFormat = "([0-9]+)([smhd]{1})".r
  implicit val durationDecoder: Decoder[FiniteDuration] = Decoder.decodeString.emapTry {
    case durationFormat(num, suffix) => Try(FiniteDuration(num.toLong, suffix))
    case d                           => Failure(new IllegalArgumentException(s"duration is in wrong format: $d"))
  }

}
