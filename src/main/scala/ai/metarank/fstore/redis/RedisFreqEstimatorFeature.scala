package ai.metarank.fstore.redis

import ai.metarank.fstore.redis.client.RedisPipeline.RedisOp
import ai.metarank.fstore.redis.client.RedisPipeline.RedisOp.{LPUSH, LTRIM}
import ai.metarank.fstore.redis.client.RedisReader
import ai.metarank.model.Feature.FreqEstimator
import ai.metarank.model.Feature.FreqEstimator.FreqEstimatorConfig
import ai.metarank.model.FeatureValue.FrequencyValue
import ai.metarank.model.{Key, Timestamp}
import ai.metarank.model.Write.PutFreqSample
import cats.effect.IO
import cats.effect.std.Queue

case class RedisFreqEstimatorFeature(config: FreqEstimatorConfig, queue: Queue[IO, RedisOp], client: RedisReader)
    extends FreqEstimator {
  override def putSampled(action: PutFreqSample): IO[Unit] = for {
    _ <- queue.offer(LPUSH(action.key.asString, action.value))
    _ <- queue.offer(LTRIM(action.key.asString, 0, config.poolSize))
  } yield {}

  override def computeValue(key: Key, ts: Timestamp): IO[Option[FrequencyValue]] = {
    client.lrange(key.asString, 0, config.poolSize).flatMap {
      case list if list.isEmpty => IO.pure(None)
      case list                 => IO(freqFromSamples(list).map(FrequencyValue(key, ts, _)))
    }
  }
}
