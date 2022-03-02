package ai.metarank.model

import ai.metarank.model.FieldName.EventType
import io.circe.{Codec, Decoder, Encoder}

import java.util.IllegalFormatException
import scala.util.{Failure, Success}

case class FieldName(event: EventType, field: String)

object FieldName {

  sealed trait EventType {
    def asString: String
  }
  case object Metadata extends EventType {
    override def asString: String = "metadata"
  }
  case class Interaction(`type`: String) extends EventType {
    override def asString: String = s"interaction:${`type`}"
  }
  case object Ranking extends EventType {
    override def asString: String = "ranking"
  }

  implicit val encoder: Encoder[FieldName] = Encoder.encodeString.contramap(x => s"${x.event.asString}.${x.field}")

  val eventPattern       = "([a-z]+)\\.([a-zA-Z0-9_]+)".r
  val interactionPattern = "interaction:([a-zA-Z0-9_]+)\\.([a-zA-Z0-9_]+)".r
  implicit val decoder: Decoder[FieldName] = Decoder.decodeString.emapTry {
    case interactionPattern(tpe, field) => Success(FieldName(Interaction(tpe), field))
    case eventPattern(source, field) =>
      source match {
        case "metadata" => Success(FieldName(Metadata, field))
        case "ranking"  => Success(FieldName(Ranking, field))
        case other      => Failure(new IllegalArgumentException(s"cannot decode source field $other"))
      }
    case other => Failure(new IllegalArgumentException(s"cannot decode source field $other"))
  }

  implicit val codec: Codec[FieldName] = Codec.from(decoder, encoder)
}
