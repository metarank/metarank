package ai.metarank.mode.inference

import ai.metarank.FeatureMapping
import ai.metarank.config.BootstrapConfig.SyntheticImpressionConfig
import ai.metarank.config.MPath
import ai.metarank.mode.AsyncFlinkJob
import ai.metarank.mode.bootstrap.Bootstrap
import ai.metarank.model.Event
import ai.metarank.util.Logging
import io.findify.featury.connector.redis.RedisStore
import io.findify.featury.flink.format.FeatureStoreSink
import io.findify.featury.model.FeatureValue
import io.findify.featury.values.StoreCodec
import io.findify.featury.values.ValueStoreConfig.RedisConfig
import io.findify.flink.api._
import org.apache.flink.api.common.typeinfo.TypeInformation

object FeedbackFlow extends Logging {
  import ai.metarank.flow.DataStreamOps._
  def resource(
      cluster: FlinkMinicluster,
      mapping: FeatureMapping,
      redisHost: String,
      redisPort: Int,
      savepoint: MPath,
      format: StoreCodec,
      impress: SyntheticImpressionConfig,
      events: StreamExecutionEnvironment => DataStream[Event]
  )(implicit
      eti: TypeInformation[Event],
      valti: TypeInformation[FeatureValue],
      intti: TypeInformation[Int],
      lti: TypeInformation[Long]
  ) = {
    AsyncFlinkJob.execute(cluster, Some(savepoint.uri)) { env =>
      {
        val source          = events(env).id("local-source")
        val grouped         = Bootstrap.groupFeedback(source)
        val (_, _, updates) = Bootstrap.makeUpdates(source, grouped, mapping, impress)
        updates
          .addSink(
            FeatureStoreSink(RedisStore(RedisConfig(redisHost, redisPort, format)), 1)
          )
          .id("write-redis")
      }
    }
  }
}
