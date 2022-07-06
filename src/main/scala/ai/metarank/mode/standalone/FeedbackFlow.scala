package ai.metarank.mode.standalone

import ai.metarank.FeatureMapping
import ai.metarank.config.BootstrapConfig.SyntheticImpressionConfig
import ai.metarank.config.MPath
import ai.metarank.mode.AsyncFlinkJob
import ai.metarank.mode.bootstrap.Bootstrap
import ai.metarank.mode.upload.WindowBatchFunction
import ai.metarank.model.Event
import ai.metarank.util.Logging
import io.findify.featury.connector.redis.RedisStore
import io.findify.featury.flink.format.FeatureStoreSink
import io.findify.featury.model.FeatureValue
import io.findify.featury.model.Key.Tenant
import io.findify.featury.values.StoreCodec
import io.findify.featury.values.ValueStoreConfig.RedisConfig
import io.findify.flink.api._
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.datastream.DataStreamSink

import scala.concurrent.duration._

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
      events: StreamExecutionEnvironment => DataStream[Event],
      batchPeriod: FiniteDuration
  )(implicit
      eti: TypeInformation[Event],
      valti: TypeInformation[FeatureValue],
      vallistti: TypeInformation[List[FeatureValue]],
      intti: TypeInformation[Int],
      lti: TypeInformation[Long],
      tti: TypeInformation[Tenant]
  ) = {
    AsyncFlinkJob.execute(cluster, Some(savepoint.uri), name = Some("metarank-update")) { env =>
      job(env, mapping, redisHost, redisPort, format, impress, events, batchPeriod)
    }
  }

  def job(
      env: StreamExecutionEnvironment,
      mapping: FeatureMapping,
      redisHost: String,
      redisPort: Int,
      format: StoreCodec,
      impress: SyntheticImpressionConfig,
      events: StreamExecutionEnvironment => DataStream[Event],
      batchPeriod: FiniteDuration
  )(implicit
      eti: TypeInformation[Event],
      valti: TypeInformation[FeatureValue],
      vallistti: TypeInformation[List[FeatureValue]],
      intti: TypeInformation[Int],
      lti: TypeInformation[Long],
      tti: TypeInformation[Tenant]
  ): DataStreamSink[List[FeatureValue]] = {
    val source          = events(env).id("local-source")
    val grouped         = Bootstrap.groupFeedback(source)
    val (_, _, updates) = Bootstrap.makeUpdates(source, grouped, mapping, impress)
    updates
      .keyBy(_.key.tenant)
      .process(WindowBatchFunction(batchPeriod, 128))
      .id("make-batch")
      .sinkTo(
        FeatureStoreSink(RedisStore(RedisConfig(redisHost, redisPort, format)))
      )
      .id("write-redis")
  }
}
