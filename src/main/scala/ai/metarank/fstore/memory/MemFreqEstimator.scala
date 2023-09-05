package ai.metarank.fstore.memory

import ai.metarank.model.Feature.FreqEstimatorFeature
import ai.metarank.model.Feature.FreqEstimatorFeature.FreqEstimatorConfig
import ai.metarank.model.FeatureValue.FrequencyValue
import ai.metarank.model.{Feature, Key, Timestamp}
import ai.metarank.model.Write.PutFreqSample
import cats.effect.IO
import com.github.blemale.scaffeine.{Cache, Scaffeine}
import shapeless.syntax.typeable._

case class MemFreqEstimator(config: FreqEstimatorConfig, cache: Cache[Key, AnyRef] = Scaffeine().build())
    extends FreqEstimatorFeature {
  override def put(action: PutFreqSample): IO[Unit] = IO {
    if (Feature.shouldSample(config.sampleRate)) {
      cache.getIfPresent(action.key).flatMap(_.cast[List[String]]) match {
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
      pool <- cache.getIfPresent(key).flatMap(_.cast[List[String]]) if pool.nonEmpty
      freq <- freqFromSamples(pool)
    } yield {
      FrequencyValue(key, ts, freq, config.ttl)
    }
  }

}
