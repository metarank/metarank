package ai.metarank.model

import ai.metarank.model.Event.{ItemEvent, RankingEvent}
import ai.metarank.model.Identifier.{ItemId, SessionId, UserId}
import ai.metarank.model.ScopeType.{GlobalScopeType, ItemScopeType, SessionScopeType, UserScopeType}
import io.circe.{Codec, Decoder, Encoder}

sealed trait Scope {
  def asString: String
  def getType: ScopeType
}

object Scope {
  case class UserScope(env: Env, user: UserId) extends Scope {
    override def asString: String   = s"user=${user.value}@${env.value}"
    override def getType: ScopeType = UserScopeType
  }

  case class ItemScope(env: Env, item: ItemId) extends Scope {
    override def asString: String   = s"item=${item.value}@${env.value}"
    override def getType: ScopeType = ItemScopeType
  }

  case class GlobalScope(env: Env) extends Scope {
    override def asString: String   = s"global=${env.value}"
    override def getType: ScopeType = GlobalScopeType
  }

  case class SessionScope(env: Env, session: SessionId) extends Scope {
    override def asString: String   = s"session=${session.value}@${env.value}"
    override def getType: ScopeType = SessionScopeType
  }

  def fromString(str: String): Either[Throwable, Scope] = {
    val firstEq = str.indexOf("=")
    if (firstEq > 0) {
      val scope = str.substring(0, firstEq)
      scope match {
        case "global" => Right(GlobalScope(Env(str.substring(firstEq + 1))))
        case _ =>
          val dogPos = str.lastIndexOf("@")
          if (dogPos > 0) {
            val id  = str.substring(firstEq + 1, dogPos)
            val env = str.substring(dogPos + 1)
            scope match {
              case "item"    => Right(ItemScope(Env(env), ItemId(id)))
              case "user"    => Right(UserScope(Env(env), UserId(id)))
              case "session" => Right(SessionScope(Env(env), SessionId(id)))
              case other     => Left(new Exception(s"scope type $other not supported"))
            }
          } else {
            Left(new Exception(s"cannot decode scope $str"))
          }
      }
    } else {
      Left(new Exception(s"cannot decode scope $str"))
    }
  }

  implicit val scopeDecoder: Decoder[Scope] = Decoder.decodeString.emapTry(str => Scope.fromString(str).toTry)
  implicit val scopeEncoder: Encoder[Scope] = Encoder.encodeString.contramap(_.asString)
  implicit val scopeCodec: Codec[Scope]     = Codec.from(scopeDecoder, scopeEncoder)

}
