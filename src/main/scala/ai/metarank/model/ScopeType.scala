package ai.metarank.model

import io.circe.{Decoder, Encoder}

import scala.util.{Failure, Success}

sealed trait ScopeType

object ScopeType {
  sealed trait FieldScopeType extends ScopeType {
    def field: String
  }
  object FieldScopeType {
    def unapply(f: ScopeType): Option[String] = f match {
      case ItemFieldScopeType(f)    => Some(f)
      case RankingFieldScopeType(f) => Some(f)
      case _                        => None
    }
  }
  case object GlobalScopeType                     extends ScopeType
  case object ItemScopeType                       extends ScopeType
  case object RankingScopeType                    extends ScopeType
  case class ItemFieldScopeType(field: String)    extends ScopeType with FieldScopeType
  case class RankingFieldScopeType(field: String) extends ScopeType with FieldScopeType
  case object UserScopeType                       extends ScopeType
  case object SessionScopeType                    extends ScopeType

  implicit val scopeEncoder: Encoder[ScopeType] = Encoder.encodeString.contramap {
    case GlobalScopeType              => "global"
    case ItemScopeType                => "item"
    case ItemFieldScopeType(field)    => s"item.$field"
    case RankingFieldScopeType(field) => s"ranking.$field"
    case UserScopeType                => "user"
    case SessionScopeType             => "session"
    case RankingScopeType             => "ranking"
  }

  val itemFieldFormat      = "item\\.([a-zA-Z0-9\\-_]+)".r
  val fieldItemFieldFormat = "ranking\\.([a-zA-Z0-9\\-_]+)".r
  implicit val scopeDecoder: Decoder[ScopeType] = Decoder.decodeString.emapTry {
    case "global"                    => Success(GlobalScopeType)
    case "item"                      => Success(ItemScopeType)
    case itemFieldFormat(field)      => Success(ItemFieldScopeType(field))
    case fieldItemFieldFormat(field) => Success(RankingFieldScopeType(field))
    case "user"                      => Success(UserScopeType)
    case "session"                   => Success(SessionScopeType)
    case "ranking"                   => Success(RankingScopeType)
    case other                       => Failure(new Exception(s"scope type $other not supported"))
  }
}
