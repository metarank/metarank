package ai.metarank.flow

import ai.metarank.model.Clickthrough
import ai.metarank.model.FeatureScope.{ItemScope, SessionScope, TenantScope, UserScope}
import io.findify.featury.flink.Join
import io.findify.featury.model.{FeatureValue, Key}
import io.findify.featury.model.Key.Tenant

object ClickthroughJoin extends Join[Clickthrough] {
  val scopes = List(
    TenantScope,
    UserScope,
    ItemScope,
    SessionScope
  )
  override def by(left: Clickthrough): Key.Tenant = Tenant(left.ranking.tenant)

  override def tags(left: Clickthrough): List[Key.Tag] = scopes.flatMap(_.tags(left.ranking))

  override def join(left: Clickthrough, values: List[FeatureValue]): Clickthrough =
    left.copy(features = left.features ++ values)
}
