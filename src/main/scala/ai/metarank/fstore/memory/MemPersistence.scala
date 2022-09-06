package ai.metarank.fstore.memory

import ai.metarank.fstore.Persistence
import ai.metarank.fstore.Persistence.KVCodec
import ai.metarank.model.{FeatureValue, Key, Schema}
import ai.metarank.rank.Model.Scorer
import cats.effect.IO

case class MemPersistence(schema: Schema) extends Persistence {
  override lazy val counters         = schema.counters.view.mapValues(MemCounter(_)).toMap
  override lazy val periodicCounters = schema.periodicCounters.view.mapValues(MemPeriodicCounter(_)).toMap
  override lazy val lists            = schema.lists.view.mapValues(MemBoundedList(_)).toMap
  override lazy val freqs            = schema.freqs.view.mapValues(MemFreqEstimator(_)).toMap
  override lazy val scalars          = schema.scalars.view.mapValues(MemScalarFeature(_)).toMap
  override lazy val stats            = schema.stats.view.mapValues(MemStatsEstimator(_)).toMap
  override lazy val maps             = schema.maps.view.mapValues(MemMapFeature(_)).toMap

  override lazy val models: Persistence.KVStore[Persistence.ModelName, Scorer] = MemKVStore()
  override lazy val values: Persistence.KVStore[Key, FeatureValue]             = MemKVStore()

  override lazy val cts: Persistence.ClickthroughStore = MemClickthroughStore()
  override def healthcheck(): IO[Unit]                 = IO.unit
  override def sync: IO[Unit]                          = IO.unit

}
