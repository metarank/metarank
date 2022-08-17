package ai.metarank.model

import io.circe.{Decoder, Encoder}

import scala.util.{Failure, Success}

sealed trait ScopeType

object ScopeType {
  case object GlobalScopeType  extends ScopeType
  case object ItemScopeType    extends ScopeType
  case object UserScopeType    extends ScopeType
  case object SessionScopeType extends ScopeType

  implicit val scopeEncoder: Encoder[ScopeType] = Encoder.encodeString.contramap {
    case GlobalScopeType  => "global"
    case ItemScopeType    => "item"
    case UserScopeType    => "user"
    case SessionScopeType => "session"
  }

  implicit val scopeDecoder: Decoder[ScopeType] = Decoder.decodeString.emapTry {
    case "global"  => Success(GlobalScopeType)
    case "item"    => Success(ItemScopeType)
    case "user"    => Success(UserScopeType)
    case "session" => Success(SessionScopeType)
    case other     => Failure(new Exception(s"scope type $other not supported"))
  }
}
