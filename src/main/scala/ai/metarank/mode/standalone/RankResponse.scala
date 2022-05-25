package ai.metarank.mode.standalone

import ai.metarank.mode.standalone.RankResponse.{ItemScore, StateValues}
import ai.metarank.model.FeatureScope.{ItemScope, SessionScope, TenantScope, UserScope}
import ai.metarank.model.MValue
import ai.metarank.model.Identifier._
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import io.findify.featury.model.FeatureValue

case class RankResponse(state: StateValues, items: List[ItemScore])

object RankResponse {
  case class StateValues(
      session: List[FeatureValue],
      user: List[FeatureValue],
      tenant: List[FeatureValue],
      item: List[FeatureValue]
  )
  object StateValues {
    def apply(values: List[FeatureValue]) = {
      new StateValues(
        session = values.filter(_.key.tag.scope == SessionScope.scope),
        user = values.filter(_.key.tag.scope == UserScope.scope),
        tenant = values.filter(_.key.tag.scope == TenantScope.scope),
        item = values.filter(_.key.tag.scope == ItemScope.scope)
      )
    }
  }

  case class ItemScore(item: ItemId, score: Double, features: List[MValue])
  import io.findify.featury.model.json.FeatureValueJson._
  implicit val itemScoreCodec: Codec[ItemScore]       = deriveCodec
  implicit val stateValuesCodec: Codec[StateValues]   = deriveCodec
  implicit val rankResponseCodec: Codec[RankResponse] = deriveCodec
}
