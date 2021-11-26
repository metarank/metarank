package ai.metarank.mode

import org.apache.flink.api.common.state.StateTtlConfig

import scala.concurrent.duration.FiniteDuration

object StateTtl {
  def apply(ttl: FiniteDuration) = StateTtlConfig
    .newBuilder(org.apache.flink.api.common.time.Time.seconds(ttl.toSeconds))
    .setUpdateType(StateTtlConfig.UpdateType.OnCreateAndWrite)
    .setStateVisibility(StateTtlConfig.StateVisibility.NeverReturnExpired)
    .cleanupFullSnapshot()
    .build()
}
