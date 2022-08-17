package ai.metarank.fstore.memory

import ai.metarank.model.Feature.FreqEstimator
import ai.metarank.model.Feature.FreqEstimator.FreqEstimatorConfig
import ai.metarank.model.FeatureValue.FrequencyValue
import ai.metarank.model.{Feature, Key, Timestamp}
import ai.metarank.model.Write.PutFreqSample
import cats.effect.IO
import com.github.blemale.scaffeine.{Cache, Scaffeine}

case class MemFreqEstimator(config: FreqEstimatorConfig, cache: Cache[Key, List[String]] = Scaffeine().build())
    extends FreqEstimator {
  override def put(action: PutFreqSample): IO[Unit] = IO {
    if (Feature.shouldSample(config.sampleRate)) {
      cache.getIfPresent(action.key) match {
        case Some(pool) if pool.size < config.poolSize =>
          cache.put(action.key, action.value +: pool)
        case Some(pool) =>
          cache.put(action.key, (action.value +: pool).take(config.poolSize))
        case None =>
          cache.put(action.key, List(action.value))
      }
    } else {}
  }

  override def computeValue(key: Key, ts: Timestamp): IO[Option[FrequencyValue]] = IO {
    for {
      pool <- cache.getIfPresent(key) if pool.nonEmpty
      freq <- freqFromSamples(pool)
    } yield {
      FrequencyValue(key, ts, freq)
    }
  }

}
