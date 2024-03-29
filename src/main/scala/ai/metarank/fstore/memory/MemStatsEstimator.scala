package ai.metarank.fstore.memory

import ai.metarank.model.Feature.StatsEstimatorFeature
import ai.metarank.model.Feature.StatsEstimatorFeature.StatsEstimatorConfig
import ai.metarank.model.FeatureValue.NumStatsValue
import ai.metarank.model.Write.PutStatSample
import ai.metarank.model.{Feature, Key, Timestamp}
import cats.effect.IO
import com.github.blemale.scaffeine.{Cache, Scaffeine}
import shapeless.syntax.typeable._

case class MemStatsEstimator(config: StatsEstimatorConfig, cache: Cache[Key, AnyRef] = Scaffeine().build())
    extends StatsEstimatorFeature {
  override def put(action: PutStatSample): IO[Unit] = IO {
    if (Feature.shouldSample(config.sampleRate)) {
      cache.getIfPresent(action.key).flatMap(_.cast[List[Double]]) match {
        case None =>
          cache.put(action.key, List(action.value))
        case Some(pool) if pool.size < config.poolSize =>
          cache.put(action.key, action.value +: pool)
        case Some(pool) =>
          cache.put(action.key, (action.value +: pool).take(config.poolSize))
      }
    } else {}
  }

  override def computeValue(key: Key, ts: Timestamp): IO[Option[NumStatsValue]] = IO {
    for {
      pool <- cache.getIfPresent(key).flatMap(_.cast[List[Double]]) if pool.nonEmpty
    } yield {
      fromPool(key, ts, pool)
    }
  }
}
