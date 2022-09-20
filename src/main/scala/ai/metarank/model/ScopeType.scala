package ai.metarank.model

import ai.metarank.util.DelimitedPair
import ai.metarank.util.DelimitedPair.DotDelimitedPair
import io.circe.{Decoder, Encoder}

import scala.util.{Failure, Success}

sealed trait ScopeType

object ScopeType {
  case object GlobalScopeType                  extends ScopeType
  case object ItemScopeType                    extends ScopeType
  case class ItemFieldScopeType(field: String) extends ScopeType
  case object UserScopeType                    extends ScopeType
  case class UserFieldScopeType(field: String) extends ScopeType
  case object SessionScopeType                 extends ScopeType

  implicit val scopeEncoder: Encoder[ScopeType] = Encoder.encodeString.contramap {
    case GlobalScopeType           => "global"
    case ItemScopeType             => "item"
    case UserScopeType             => "user"
    case SessionScopeType          => "session"
    case ItemFieldScopeType(field) => s"item.$field"
    case UserFieldScopeType(field) => s"user.${field}"
  }

  implicit val scopeDecoder: Decoder[ScopeType] = Decoder.decodeString.emapTry {
    case "global"                        => Success(GlobalScopeType)
    case "item"                          => Success(ItemScopeType)
    case "user"                          => Success(UserScopeType)
    case "session"                       => Success(SessionScopeType)
    case DotDelimitedPair("user", field) => Success(UserFieldScopeType(field))
    case DotDelimitedPair("item", field) => Success(ItemFieldScopeType(field))
    case other                           => Failure(new Exception(s"scope type $other not supported"))
  }
}
