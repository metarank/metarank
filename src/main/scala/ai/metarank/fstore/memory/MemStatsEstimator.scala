package ai.metarank.fstore.memory

import ai.metarank.model.Feature.StatsEstimator
import ai.metarank.model.Feature.StatsEstimator.StatsEstimatorConfig
import ai.metarank.model.FeatureValue.NumStatsValue
import ai.metarank.model.Write.PutStatSample
import ai.metarank.model.{Key, Timestamp}
import cats.effect.IO
import com.github.blemale.scaffeine.{Cache, Scaffeine}

import scala.util.Random

case class MemStatsEstimator(config: StatsEstimatorConfig, cache: Cache[Key, List[Double]] = Scaffeine().build())
    extends StatsEstimator {
  override def putSampled(action: PutStatSample): IO[Unit] = IO {
    cache.getIfPresent(action.key) match {
      case None =>
        cache.put(action.key, List(action.value))
      case Some(pool) if pool.size < config.poolSize =>
        cache.put(action.key, action.value +: pool)
      case Some(pool) =>
        val index = Random.nextInt(config.poolSize)
        cache.put(action.key, (action.value +: pool).take(config.poolSize))
    }
  }

  override def computeValue(key: Key, ts: Timestamp): IO[Option[NumStatsValue]] = IO {
    for {
      pool <- cache.getIfPresent(key) if pool.nonEmpty
    } yield {
      fromPool(key, ts, pool)
    }
  }
}
