package ai.metarank.model

import ai.metarank.model.Event.{ItemEvent, RankingEvent}
import ai.metarank.model.Identifier.{ItemId, SessionId, UserId}
import ai.metarank.model.ScopeType.{
  GlobalScopeType,
  ItemFieldScopeType,
  ItemScopeType,
  SessionScopeType,
  UserFieldScopeType,
  UserScopeType
}
import ai.metarank.util.DelimitedPair.{DotDelimitedPair, EqualsDelimitedPair}
import io.circe.{Codec, Decoder, Encoder}

sealed trait Scope extends {
  def asString: String
  def getType: ScopeType
}

object Scope {
  case class UserScope(user: UserId) extends Scope {
    override def asString: String   = s"user=${user.value}"
    override def getType: ScopeType = UserScopeType
  }

  case class UserFieldScope(user: UserId, field: String) extends Scope {
    override def asString: String   = s"user.field=${user.value}.$field"
    override def getType: ScopeType = UserFieldScopeType(field)
  }

  case class ItemScope(item: ItemId) extends Scope {
    override def asString: String   = s"item=${item.value}"
    override def getType: ScopeType = ItemScopeType
  }

  case class ItemFieldScope(item: ItemId, field: String) extends Scope {
    override def asString: String   = s"item.field=${item.value}.$field"
    override def getType: ScopeType = ItemFieldScopeType(field)
  }

  case object GlobalScope extends Scope {
    override def asString: String   = "global"
    override def getType: ScopeType = GlobalScopeType
  }

  case class SessionScope(session: SessionId) extends Scope {
    override def asString: String   = s"session=${session.value}"
    override def getType: ScopeType = SessionScopeType
  }

  def fromString(str: String): Either[Throwable, Scope] = {
    str match {
      case "global"                                => Right(GlobalScope)
      case EqualsDelimitedPair("item", item)       => Right(ItemScope(ItemId(item)))
      case EqualsDelimitedPair("user", user)       => Right(UserScope(UserId(user)))
      case EqualsDelimitedPair("session", session) => Right(SessionScope(SessionId(session)))
      case EqualsDelimitedPair("user.field", DotDelimitedPair(user, field)) =>
        Right(UserFieldScope(UserId(user), field))
      case EqualsDelimitedPair("item.field", DotDelimitedPair(item, field)) =>
        Right(ItemFieldScope(ItemId(item), field))
      case other => Left(new IllegalArgumentException(s"cannot parse scope $other"))
    }
  }

  implicit val scopeDecoder: Decoder[Scope] = Decoder.decodeString.emapTry(str => Scope.fromString(str).toTry)
  implicit val scopeEncoder: Encoder[Scope] = Encoder.encodeString.contramap(_.asString)
  implicit val scopeCodec: Codec[Scope]     = Codec.from(scopeDecoder, scopeEncoder)

}
