package ai.metarank.model

import io.circe.{Decoder, Encoder}

import scala.util.Success

sealed trait FeatureScope {
  def value: String
}

object FeatureScope {
  case object TenantScope  extends FeatureScope { val value = "tenant"  }
  case object ItemScope    extends FeatureScope { val value = "item"    }
  case object UserScope    extends FeatureScope { val value = "user"    }
  case object SessionScope extends FeatureScope { val value = "session" }

  implicit val scopeEncoder: Encoder[FeatureScope] = Encoder.encodeString.contramap(_.value)

  implicit val scopeDecoder: Decoder[FeatureScope] = Decoder.decodeString.emapTry {
    case TenantScope.value  => Success(TenantScope)
    case ItemScope.value    => Success(ItemScope)
    case UserScope.value    => Success(UserScope)
    case SessionScope.value => Success(SessionScope)
  }
}
