package ai.metarank.model

import ai.metarank.model.Identifier._
import ai.metarank.model.RankResponse.{ItemScore, StateValues}
import ai.metarank.model.ScopeType.{GlobalScopeType, ItemScopeType, SessionScopeType, UserScopeType}
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class RankResponse(state: Option[StateValues], items: List[ItemScore])

object RankResponse {
  case class StateValues(
      session: List[FeatureValue],
      user: List[FeatureValue],
      global: List[FeatureValue],
      item: List[FeatureValue]
  )
  object StateValues {
    def apply(values: List[FeatureValue]) = {
      new StateValues(
        session = values.filter(_.key.scope.getType == SessionScopeType),
        user = values.filter(_.key.scope.getType == UserScopeType),
        global = values.filter(_.key.scope.getType == GlobalScopeType),
        item = values.filter(_.key.scope.getType == ItemScopeType)
      )
    }
  }

  case class ItemScore(item: ItemId, score: Double, features: Option[List[MValue]])
  implicit val itemScoreCodec: Codec[ItemScore]       = deriveCodec
  implicit val stateValuesCodec: Codec[StateValues]   = deriveCodec
  implicit val rankResponseCodec: Codec[RankResponse] = deriveCodec
}
