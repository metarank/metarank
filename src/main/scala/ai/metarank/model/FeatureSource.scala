package ai.metarank.model

import io.circe.{Codec, Decoder, Encoder}

import java.util.IllegalFormatException
import scala.util.{Failure, Success}

sealed trait FeatureSource {
  def asString: String
}

object FeatureSource {
  case object Metadata extends FeatureSource {
    override def asString: String = "metadata"
  }
  case class Interaction(`type`: String) extends FeatureSource {
    override def asString: String = s"interaction:${`type`}"
  }
  case object Ranking extends FeatureSource {
    override def asString: String = "ranking"
  }
  case object Impression extends FeatureSource {
    override def asString: String = "impression"
  }

  implicit val encoder: Encoder[FeatureSource] = Encoder.encodeString.contramap(_.asString)

  val interactionPattern = "interaction:([a-zA-Z0-9]+)".r
  implicit val decoder: Decoder[FeatureSource] = Decoder.decodeString.emapTry {
    case "metadata"              => Success(Metadata)
    case "impression"            => Success(Impression)
    case "ranking"               => Success(Ranking)
    case interactionPattern(tpe) => Success(Interaction(tpe))
    case other                   => Failure(new IllegalArgumentException(s"cannot decode source field $other"))
  }

  implicit val codec: Codec[FeatureSource] = Codec.from(decoder, encoder)
}
