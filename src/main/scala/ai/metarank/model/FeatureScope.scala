package ai.metarank.model

import io.circe.{Decoder, Encoder}

import scala.util.Success

sealed trait FeatureScope {
  def value: String
}

object FeatureScope {
  case object GlobalScope  extends FeatureScope { val value = "global"  }
  case object ItemScope    extends FeatureScope { val value = "item"    }
  case object UserScope    extends FeatureScope { val value = "user"    }
  case object SessionScope extends FeatureScope { val value = "session" }

  implicit val scopeEncoder: Encoder[FeatureScope] = Encoder.encodeString.contramap(_.value)

  implicit val scopeDecoder: Decoder[FeatureScope] = Decoder.decodeString.emapTry {
    case "global"  => Success(GlobalScope)
    case "item"    => Success(ItemScope)
    case "user"    => Success(UserScope)
    case "session" => Success(SessionScope)
  }
}
