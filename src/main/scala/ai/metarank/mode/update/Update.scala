package ai.metarank.mode.update

import ai.metarank.FeatureMapping
import ai.metarank.config.Config
import ai.metarank.mode.FlinkJob
import ai.metarank.mode.standalone.FeedbackFlow
import ai.metarank.source.EventSource
import io.findify.flink.api.StreamExecutionEnvironment

import scala.concurrent.duration._
import io.findify.flinkadt.api._

/** Supposed to be run from the flink k8s operator, so no IO stuff here.
  */

object Update extends FlinkJob {
  import ai.metarank.mode.TypeInfos._

  override def usage = "usage: metarank update <config path>"

  override def job(config: Config, mapping: FeatureMapping, streamEnv: StreamExecutionEnvironment): Unit = {
    val source = EventSource.fromConfig(config.inference.source)
    FeedbackFlow.job(
      env = streamEnv,
      mapping = mapping,
      redisHost = config.inference.state.host,
      redisPort = config.inference.state.port,
      format = config.inference.state.format,
      impress = config.bootstrap.syntheticImpression,
      events = source.eventStream(_, bounded = false),
      batchPeriod = 100.millis
    )
    streamEnv.execute("metarank-update")
  }
}
