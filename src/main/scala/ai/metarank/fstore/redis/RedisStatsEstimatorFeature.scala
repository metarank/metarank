package ai.metarank.fstore.redis

import ai.metarank.fstore.codec.{KCodec, StoreFormat}
import ai.metarank.fstore.redis.client.RedisClient
import ai.metarank.fstore.transfer.StateSink
import ai.metarank.fstore.transfer.StateSink.TransferResult
import ai.metarank.model.Feature.{StatsEstimatorFeature, shouldSample}
import ai.metarank.model.Feature.StatsEstimatorFeature.StatsEstimatorConfig
import ai.metarank.model.FeatureValue.NumStatsValue
import ai.metarank.model.State.StatsEstimatorState
import ai.metarank.model.{Key, Timestamp}
import ai.metarank.model.Write.PutStatSample
import cats.effect.IO
import cats.implicits._

case class RedisStatsEstimatorFeature(
    config: StatsEstimatorConfig,
    client: RedisClient,
    prefix: String,
    format: StoreFormat
) extends StatsEstimatorFeature {
  override def put(action: PutStatSample): IO[Unit] = {
    if (shouldSample(config.sampleRate)) {
      val key = format.key.encode(prefix, action.key)
      client
        .lpush(key, action.value.toString.getBytes())
        .flatMap(_ => client.ltrim(key, 0, config.poolSize).void)
    } else {
      IO.unit
    }
  }

  override def computeValue(key: Key, ts: Timestamp): IO[Option[NumStatsValue]] = for {
    raw <- client.lrange(format.key.encode(prefix, key), 0, config.poolSize)
    decoded <- raw
      .map(str => IO.fromOption(new String(str).toDoubleOption)(new Exception("cannot parse double")))
      .sequence
  } yield {
    if (decoded.isEmpty) None else Some(fromPool(key, ts, decoded))
  }

}

object RedisStatsEstimatorFeature {
  implicit val statsSink: StateSink[StatsEstimatorState, RedisStatsEstimatorFeature] =
    new StateSink[StatsEstimatorState, RedisStatsEstimatorFeature] {
      override def sink(f: RedisStatsEstimatorFeature, state: fs2.Stream[IO, StatsEstimatorState]): IO[TransferResult] =
        state
          .evalMap(s =>
            f.client.lpush(f.format.key.encode(f.prefix, s.key), s.values.toList.map(_.toString.getBytes())).map(_ => 1)
          )
          .compile
          .fold(0)(_ + _)
          .map(TransferResult.apply)
    }
}
