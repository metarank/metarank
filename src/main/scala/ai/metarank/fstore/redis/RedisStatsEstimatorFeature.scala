package ai.metarank.fstore.redis

import ai.metarank.fstore.redis.client.RedisPipeline.RedisOp
import ai.metarank.fstore.redis.client.RedisPipeline.RedisOp.{LPUSH, LTRIM}
import ai.metarank.fstore.redis.client.RedisClient
import ai.metarank.model.Feature.{StatsEstimator, shouldSample}
import ai.metarank.model.Feature.StatsEstimator.StatsEstimatorConfig
import ai.metarank.model.FeatureValue.NumStatsValue
import ai.metarank.model.{Key, Timestamp}
import ai.metarank.model.Write.PutStatSample
import cats.effect.IO
import cats.effect.std.Queue
import cats.implicits._

case class RedisStatsEstimatorFeature(config: StatsEstimatorConfig, client: RedisClient) extends StatsEstimator {
  override def put(action: PutStatSample): IO[Unit] = {
    if (shouldSample(config.sampleRate)) {
      client
        .lpush(action.key.asString, action.value.toString)
        .flatMap(_ => client.ltrim(action.key.asString, 0, config.poolSize).void)
    } else {
      IO.unit
    }
  }

  override def computeValue(key: Key, ts: Timestamp): IO[Option[NumStatsValue]] = for {
    raw     <- client.lrange(key.asString, 0, config.poolSize)
    decoded <- raw.map(str => IO.fromOption(str.toDoubleOption)(new Exception("cannot parse double"))).sequence
  } yield {
    if (decoded.isEmpty) None else Some(fromPool(key, ts, decoded))
  }
}
