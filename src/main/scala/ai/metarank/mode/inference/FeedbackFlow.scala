package ai.metarank.mode.inference

import ai.metarank.FeatureMapping
import ai.metarank.mode.bootstrap.Bootstrap
import ai.metarank.model.Event
import ai.metarank.source.LocalDirSource
import cats.effect.IO
import cats.effect.kernel.Resource
import io.findify.featury.connector.redis.RedisStore
import io.findify.featury.flink.format.FeatureStoreSink
import io.findify.featury.model.FeatureValue
import io.findify.featury.values.ValueStoreConfig.RedisConfig
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.apache.flink.api.common.typeinfo.TypeInformation

object FeedbackFlow {
  def resource(path: String, mapping: FeatureMapping, cmd: InferenceCmdline)(implicit
      eti: TypeInformation[Event],
      valti: TypeInformation[FeatureValue],
      intti: TypeInformation[Int]
  ) =
    Resource.make(IO {
      val env          = StreamExecutionEnvironment.getExecutionEnvironment
      val source       = env.addSource(new LocalDirSource(path))
      val grouped      = Bootstrap.groupFeedback(source)
      val (_, updates) = Bootstrap.makeUpdates(source, grouped, mapping)
      updates.addSink(
        FeatureStoreSink(RedisStore(RedisConfig(cmd.redisHost, cmd.redisPort, cmd.format)), cmd.batchSize)
      )
      env.executeAsync("feedback")
    })(job => IO.fromCompletableFuture(IO { job.cancel() }).map(_ => Unit))
}
