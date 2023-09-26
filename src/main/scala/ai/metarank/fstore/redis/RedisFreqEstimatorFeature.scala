package ai.metarank.fstore.redis

import ai.metarank.fstore.codec.{KCodec, StoreFormat}
import ai.metarank.fstore.redis.client.RedisClient
import ai.metarank.fstore.transfer.StateSink
import ai.metarank.fstore.transfer.StateSink.TransferResult
import ai.metarank.model.Feature.{FreqEstimatorFeature, shouldSample}
import ai.metarank.model.Feature.FreqEstimatorFeature.FreqEstimatorConfig
import ai.metarank.model.FeatureValue.FrequencyValue
import ai.metarank.model.State.FreqEstimatorState
import ai.metarank.model.{Key, Timestamp}
import ai.metarank.model.Write.PutFreqSample
import cats.effect.IO

case class RedisFreqEstimatorFeature(
    config: FreqEstimatorConfig,
    client: RedisClient,
    prefix: String,
    format: StoreFormat
) extends FreqEstimatorFeature {
  override def put(action: PutFreqSample): IO[Unit] = {
    if (shouldSample(config.sampleRate)) {
      val key = format.key.encode(prefix, action.key)
      for {
        _ <- client.lpush(key, action.value.getBytes()) *> client.ltrim(key, 0, config.poolSize)
        _ <- client.expire(key, config.ttl)
      } yield {}

    } else {
      IO.unit
    }
  }

  override def computeValue(key: Key, ts: Timestamp): IO[Option[FrequencyValue]] = {
    client.lrange(format.key.encode(prefix, key), 0, config.poolSize).flatMap {
      case list if list.isEmpty => IO.pure(None)
      case list => IO(freqFromSamples(list.map(new String(_))).map(FrequencyValue(key, ts, _, config.ttl)))
    }
  }
}

object RedisFreqEstimatorFeature {
  implicit val freqSink: StateSink[FreqEstimatorState, RedisFreqEstimatorFeature] =
    new StateSink[FreqEstimatorState, RedisFreqEstimatorFeature] {
      override def sink(f: RedisFreqEstimatorFeature, state: fs2.Stream[IO, FreqEstimatorState]): IO[TransferResult] =
        state
          .evalMap(fs =>
            for {
              key <- IO(f.format.key.encode(f.prefix, fs.key))
              _   <- f.client.lpush(key, fs.values.map(_.getBytes()))
              _   <- f.client.expire(key, f.config.ttl)
            } yield { 1 }
          )
          .compile
          .fold(0)(_ + _)
          .map(TransferResult.apply)
    }
}
