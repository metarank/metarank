package ai.metarank.fstore.codec.impl

import ai.metarank.model.Identifier.{ItemId, RankingId, SessionId, UserId}
import ai.metarank.model.Scope
import ai.metarank.model.Scope.{
  GlobalScope,
  ItemFieldScope,
  ItemScope,
  RankingFieldScope,
  RankingScope,
  SessionScope,
  UserScope
}

import scala.annotation.switch

object ScopeCodec {
  def encode(scope: Scope): String = scope match {
    case UserScope(user)                                => s"user=${user.value}"
    case ItemScope(item)                                => s"item=${item.value}"
    case RankingScope(item)                             => s"ranking=${item.value}"
    case ItemFieldScope(fieldName, fieldValue)          => s"field=$fieldName:$fieldValue"
    case RankingFieldScope(fieldName, fieldValue, item) => s"irf=$fieldName:$fieldValue:${item.value}"
    case GlobalScope                                    => "global"
    case SessionScope(session)                          => s"session=${session.value}"
  }
  def decode(str: String): Either[Throwable, Scope] = {
    str match {
      case "global" => Right(GlobalScope)
      case other =>
        val firstEq = other.indexOf('='.toInt)
        if (firstEq > 0) {
          val left  = other.substring(0, firstEq)
          val right = other.substring(firstEq + 1)
          (left: @switch) match {
            case "item"    => Right(ItemScope(ItemId(right)))
            case "session" => Right(SessionScope(SessionId(right)))
            case "ranking" => Right(RankingScope(RankingId(right)))
            case "user"    => Right(UserScope(UserId(right)))
            case "field" =>
              val fieldEndPos = right.indexOf(':')
              if (fieldEndPos > 0) {
                val field = right.substring(0, fieldEndPos)
                val value = right.substring(fieldEndPos + 1)
                Right(ItemFieldScope(field, value))
              } else {
                Left(new IllegalArgumentException(s"cannot parse field scope value '$right'"))
              }
            case "irf" =>
              val fieldEndSemiPos  = right.indexOf(':')
              val itemStartSemiPos = right.lastIndexOf(':')
              if ((fieldEndSemiPos > 0) && (itemStartSemiPos > 0) && (itemStartSemiPos > fieldEndSemiPos)) {
                val field = right.substring(0, fieldEndSemiPos)
                val value = right.substring(fieldEndSemiPos + 1, itemStartSemiPos)
                val item  = right.substring(itemStartSemiPos + 1)
                Right(RankingFieldScope(field, value, ItemId(item)))
              } else {
                Left(new IllegalArgumentException(s"cannot parse item field scope value '$right'"))
              }
            case _ => Left(new IllegalArgumentException(s"cannot parse scope $other"))
          }
        } else {
          Left(new IllegalArgumentException(s"cannot parse scope $other"))
        }
    }
  }
}
