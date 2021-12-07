package ai.metarank.flow

import ai.metarank.model.EventState
import io.findify.featury.flink.Join
import io.findify.featury.model.Key.Tenant
import io.findify.featury.model.{FeatureValue, Key}

object EventStateJoin extends Join[EventState] {
  override def by(left: EventState): Key.Tenant = Tenant(left.event.tenant)

  override def tags(left: EventState): List[Key.Tag] = ClickthroughJoin.scopes.flatMap(_.tags(left.event))

  override def join(left: EventState, values: List[FeatureValue]): EventState =
    left.copy(state = left.state ++ values.map(fv => fv.key -> fv).toMap)
}
