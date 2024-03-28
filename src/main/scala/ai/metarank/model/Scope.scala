package ai.metarank.model

import ai.metarank.fstore.codec.impl.ScopeCodec
import ai.metarank.model.Event.{ItemEvent, RankingEvent}
import ai.metarank.model.Identifier.{ItemId, RankingId, SessionId, UserId}
import ai.metarank.model.ScopeType.{GlobalScopeType, ItemFieldScopeType, ItemScopeType, RankingFieldScopeType, RankingScopeType, SessionScopeType, UserScopeType}
import io.circe.{Codec, Decoder, Encoder}

import scala.annotation.switch

sealed trait Scope extends {
  def asString: String
  def getType: ScopeType
}

object Scope {
  case class UserScope(user: UserId) extends Scope {
    override val hashCode: Int      = user.value.hashCode
    override val asString: String   = ScopeCodec.encode(this)
    override val getType: ScopeType = UserScopeType
  }

  case class ItemScope(item: ItemId) extends Scope {
    override val hashCode: Int      = item.value.hashCode
    override val asString: String   = ScopeCodec.encode(this)
    override val getType: ScopeType = ItemScopeType
  }

  case class RankingScope(item: RankingId) extends Scope {
    override val hashCode: Int      = item.value.hashCode
    override val asString: String   = ScopeCodec.encode(this)
    override val getType: ScopeType = RankingScopeType
  }

  case class ItemFieldScope(fieldName: String, fieldValue: String) extends Scope {
    override val hashCode: Int      = fieldName.hashCode ^ fieldValue.hashCode
    override val asString: String   = ScopeCodec.encode(this)
    override val getType: ScopeType = ItemFieldScopeType(fieldName)
  }

  case class RankingFieldScope(fieldName: String, fieldValue: String, item: ItemId) extends Scope {
    override val hashCode: Int      = fieldName.hashCode ^ fieldValue.hashCode ^ item.value.hashCode
    override val asString: String   = ScopeCodec.encode(this)
    override val getType: ScopeType = RankingFieldScopeType(fieldName)
  }

  case object GlobalScope extends Scope {
    override val hashCode: Int      = 20221223
    override val asString: String   = ScopeCodec.encode(this)
    override val getType: ScopeType = GlobalScopeType
  }

  case class SessionScope(session: SessionId) extends Scope {
    override val hashCode: Int      = session.value.hashCode
    override val asString: String   = ScopeCodec.encode(this)
    override val getType: ScopeType = SessionScopeType
  }


  implicit val scopeDecoder: Decoder[Scope] = Decoder.decodeString.emapTry(str => ScopeCodec.decode(str).toTry)
  implicit val scopeEncoder: Encoder[Scope] = Encoder.encodeString.contramap(_.asString)
  implicit val scopeCodec: Codec[Scope]     = Codec.from(scopeDecoder, scopeEncoder)

}
