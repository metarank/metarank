package ai.metarank.util

import ai.metarank.FeatureMapping
import cats.effect.IO
import io.findify.featury.model.FeatureConfig.{
  BoundedListConfig,
  CounterConfig,
  FreqEstimatorConfig,
  MapConfig,
  PeriodicCounterConfig,
  ScalarConfig,
  StatsEstimatorConfig
}
import io.findify.featury.model.{
  BoundedListValue,
  CounterValue,
  FeatureKey,
  FeatureValue,
  FrequencyValue,
  MapValue,
  NumStatsValue,
  PeriodicCounterValue,
  SDouble,
  ScalarValue,
  TimeValue,
  Timestamp
}
import io.findify.featury.model.api.{ReadRequest, ReadResponse}
import io.findify.featury.values.FeatureStore

import scala.util.Random

case class RandomFeatureStore(mapping: FeatureMapping, seed: Int = 0) extends FeatureStore {
  val random                                              = new Random(seed)
  override def write(batch: List[FeatureValue]): IO[Unit] = IO.unit

  override def writeSync(batch: List[FeatureValue]): Unit = {}

  override def read(request: ReadRequest): IO[ReadResponse] = IO {
    val values: List[FeatureValue] = for {
      key     <- request.keys
      feature <- mapping.schema.configs.get(FeatureKey(key.tag.scope, key.name))
    } yield {
      val now = Timestamp.now
      feature match {
        case f: CounterConfig         => CounterValue(key, now, random.nextInt(100))
        case f: ScalarConfig          => ScalarValue(key, now, SDouble(random.nextDouble()))
        case f: MapConfig             => MapValue(key, now, Map.empty)
        case f: BoundedListConfig     => BoundedListValue(key, now, List(TimeValue(now, SDouble(random.nextInt(100)))))
        case f: FreqEstimatorConfig   => FrequencyValue(key, now, Map.empty)
        case f: PeriodicCounterConfig => PeriodicCounterValue(key, now, Nil)
        case f: StatsEstimatorConfig  => NumStatsValue(key, now, 0, 1, Map.empty)
      }
    }
    ReadResponse(values)
  }

  override def close(): IO[Unit] = IO.unit

  override def closeSync(): Unit = {}

}
