package ai.metarank.model

import ai.metarank.model.Event.{ItemEvent, RankingEvent}
import ai.metarank.model.Identifier.{ItemId, SessionId, UserId}
import ai.metarank.model.ScopeType.{GlobalScopeType, ItemScopeType, SessionScopeType, UserScopeType}
import io.circe.{Codec, Decoder, Encoder}

sealed trait Scope extends {
  def asString: String
  def getType: ScopeType
}

object Scope {
  case class UserScope(user: UserId) extends Scope {
    override val hashCode: Int      = user.value.hashCode
    override val asString: String   = s"user=${user.value}"
    override val getType: ScopeType = UserScopeType
  }

  case class ItemScope(item: ItemId) extends Scope {
    override val hashCode: Int      = item.value.hashCode
    override val asString: String   = s"item=${item.value}"
    override val getType: ScopeType = ItemScopeType
  }

  case object GlobalScope extends Scope {
    override val hashCode: Int      = 20221223
    override val asString: String   = "global"
    override val getType: ScopeType = GlobalScopeType
  }

  case class SessionScope(session: SessionId) extends Scope {
    override val hashCode: Int      = session.value.hashCode
    override val asString: String   = s"session=${session.value}"
    override val getType: ScopeType = SessionScopeType
  }

  def fromString(str: String): Either[Throwable, Scope] = {
    def split(s: String): Option[(String, String)] = {
      val firstEq = s.indexOf('='.toInt)
      if (firstEq > 0) {
        val left  = s.substring(0, firstEq)
        val right = s.substring(firstEq + 1)
        Some(left -> right)
      } else {
        None
      }
    }
    str match {
      case "global" => Right(GlobalScope)
      case other =>
        split(other) match {
          case Some(("item", item))    => Right(ItemScope(ItemId(item)))
          case Some(("session", sess)) => Right(SessionScope(SessionId(sess)))
          case Some(("user", user))    => Right(UserScope(UserId(user)))
          case _                       => Left(new IllegalArgumentException(s"cannot parse scope $other"))
        }
    }
  }

  implicit val scopeDecoder: Decoder[Scope] = Decoder.decodeString.emapTry(str => Scope.fromString(str).toTry)
  implicit val scopeEncoder: Encoder[Scope] = Encoder.encodeString.contramap(_.asString)
  implicit val scopeCodec: Codec[Scope]     = Codec.from(scopeDecoder, scopeEncoder)

}
