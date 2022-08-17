package ai.metarank.model

import ai.metarank.model.FieldName.EventType
import ai.metarank.model.FieldName.EventType._
import io.circe.{Codec, Decoder, Encoder}

import scala.util.{Failure, Success}

case class FieldName(event: EventType, field: String)

object FieldName {

  sealed trait EventType {
    def asString: String
  }
  object EventType {
    case object Item extends EventType {
      override def asString: String = "item"
    }
    case object User extends EventType {
      override def asString: String = "user"
    }
    case class Interaction(`type`: String) extends EventType {
      override def asString: String = s"interaction:${`type`}"
    }
    case object Ranking extends EventType {
      override def asString: String = "ranking"
    }
    case object AnyEvent extends EventType {
      override def asString: String = "*"
    }
  }

  implicit val encoder: Encoder[FieldName] = Encoder.encodeString.contramap(x => s"${x.event.asString}.${x.field}")

  val eventPattern       = "([a-z\\*]+)\\.([a-zA-Z0-9_]+)".r
  val interactionPattern = "interaction:([a-zA-Z0-9_]+)\\.([a-zA-Z0-9_]+)".r
  implicit val decoder: Decoder[FieldName] = Decoder.decodeString.emapTry {
    case interactionPattern(tpe, field) => Success(FieldName(Interaction(tpe), field))
    case eventPattern(source, field) =>
      source match {
        case "*"        => Success(FieldName(AnyEvent, field))
        case "metadata" => Success(FieldName(Item, field))
        case "item"     => Success(FieldName(Item, field))
        case "user"     => Success(FieldName(User, field))
        case "ranking"  => Success(FieldName(Ranking, field))
        case other      => Failure(new IllegalArgumentException(s"cannot decode source field $other"))
      }
    case other => Failure(new IllegalArgumentException(s"cannot decode source field $other"))
  }

  implicit val codec: Codec[FieldName] = Codec.from(decoder, encoder)
}
