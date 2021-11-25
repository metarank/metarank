package ai.metarank.model

import ai.metarank.model.Event.{InteractionEvent, RankingEvent}
import ai.metarank.model.FeatureScope.{ItemScope, SessionScope, TenantScope, UserScope}
import io.findify.featury.flink.Join
import io.findify.featury.model.Key.{Scope, Tag, Tenant}
import io.findify.featury.model.{FeatureValue, Key}

case class Clickthrough(
    ranking: RankingEvent,
    clicks: List[InteractionEvent],
    features: List[FeatureValue] = Nil
)

object Clickthrough {
  case object CTJoin extends Join[Clickthrough] {
    override def by(left: Clickthrough): Key.Tenant = Tenant(left.ranking.tenant)

    override def tags(left: Clickthrough): List[Key.Tag] = {
      List.concat(
        left.ranking.items.map(id => Tag(Scope(ItemScope.value), id.id.value)),
        List(
          Tag(Scope(UserScope.value), left.ranking.user.value),
          Tag(Scope(SessionScope.value), left.ranking.session.value),
          Tag(Scope(TenantScope.value), left.ranking.tenant)
        )
      )
    }

    override def join(left: Clickthrough, values: List[FeatureValue]): Clickthrough =
      left.copy(features = left.features ++ values)
  }

}
