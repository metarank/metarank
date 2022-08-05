package ai.metarank.fstore.redis

import ai.metarank.fstore.redis.client.RedisPipeline.RedisOp
import ai.metarank.fstore.redis.client.RedisPipeline.RedisOp.{LPUSH, LTRIM}
import ai.metarank.fstore.redis.client.RedisClient
import ai.metarank.model.Feature.{FreqEstimator, shouldSample}
import ai.metarank.model.Feature.FreqEstimator.FreqEstimatorConfig
import ai.metarank.model.FeatureValue.FrequencyValue
import ai.metarank.model.{Key, Timestamp}
import ai.metarank.model.Write.PutFreqSample
import cats.effect.IO
import cats.effect.std.Queue

case class RedisFreqEstimatorFeature(config: FreqEstimatorConfig, client: RedisClient) extends FreqEstimator {
  override def put(action: PutFreqSample): IO[Unit] = {
    if (shouldSample(config.sampleRate)) {
      val key = action.key.asString
      client.lpush(key, action.value) *> client.ltrim(key, 0, config.poolSize).void
    } else {
      IO.unit
    }
  }

  override def computeValue(key: Key, ts: Timestamp): IO[Option[FrequencyValue]] = {
    client.lrange(key.asString, 0, config.poolSize).flatMap {
      case list if list.isEmpty => IO.pure(None)
      case list                 => IO(freqFromSamples(list).map(FrequencyValue(key, ts, _)))
    }
  }
}
