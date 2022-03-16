package ai.metarank.mode.inference

import ai.metarank.FeatureMapping
import ai.metarank.mode.AsyncFlinkJob
import ai.metarank.mode.bootstrap.Bootstrap
import ai.metarank.model.Event
import ai.metarank.source.LocalDirSource
import ai.metarank.util.Logging
import cats.effect.IO
import cats.effect.kernel.Resource
import io.findify.featury.connector.redis.RedisStore
import io.findify.featury.flink.format.FeatureStoreSink
import io.findify.featury.model.FeatureValue
import io.findify.featury.values.ValueStoreConfig.RedisConfig
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.runtime.jobgraph.SavepointRestoreSettings
import org.apache.flink.streaming.util.TestStreamEnvironment

object FeedbackFlow extends Logging {
  import ai.metarank.flow.DataStreamOps._
  def resource(
      cluster: FlinkMinicluster,
      path: String,
      mapping: FeatureMapping,
      cmd: InferenceCmdline,
      redisHost: String
  )(implicit
      eti: TypeInformation[Event],
      valti: TypeInformation[FeatureValue],
      intti: TypeInformation[Int]
  ) = {
    AsyncFlinkJob.execute(cluster, Some(cmd.savepoint)) { env =>
      {
        val source       = env.addSource(new LocalDirSource(path)).id("local-source")
        val grouped      = Bootstrap.groupFeedback(source)
        val (_, updates) = Bootstrap.makeUpdates(source, grouped, mapping)
        updates
          .addSink(
            FeatureStoreSink(RedisStore(RedisConfig(redisHost, cmd.redisPort, cmd.format)), cmd.batchSize)
          )
          .id("write-redis")
      }
    }
  }
}
