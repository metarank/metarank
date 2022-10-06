package ai.metarank.fstore.redis

import ai.metarank.fstore.redis.client.RedisClient
import ai.metarank.fstore.redis.encode.{EncodeFormat, KCodec}
import ai.metarank.model.Feature.{FreqEstimatorFeature, shouldSample}
import ai.metarank.model.Feature.FreqEstimatorFeature.FreqEstimatorConfig
import ai.metarank.model.FeatureValue.FrequencyValue
import ai.metarank.model.{Key, Timestamp}
import ai.metarank.model.Write.PutFreqSample
import cats.effect.IO

case class RedisFreqEstimatorFeature(
    config: FreqEstimatorConfig,
    client: RedisClient,
    prefix: String,
    format: EncodeFormat
) extends FreqEstimatorFeature {
  override def put(action: PutFreqSample): IO[Unit] = {
    if (shouldSample(config.sampleRate)) {
      val key = keyEnc.encode(prefix, action.key)
      client.lpush(key, action.value.getBytes()) *> client.ltrim(key, 0, config.poolSize).void
    } else {
      IO.unit
    }
  }

  override def computeValue(key: Key, ts: Timestamp): IO[Option[FrequencyValue]] = {
    client.lrange(keyEnc.encode(prefix, key), 0, config.poolSize).flatMap {
      case list if list.isEmpty => IO.pure(None)
      case list                 => IO(freqFromSamples(list.map(new String(_))).map(FrequencyValue(key, ts, _)))
    }
  }
}
