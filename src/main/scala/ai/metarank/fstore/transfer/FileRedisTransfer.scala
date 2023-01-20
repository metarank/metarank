package ai.metarank.fstore.transfer

import ai.metarank.FeatureMapping
import ai.metarank.fstore.Persistence
import ai.metarank.fstore.file.{
  FileBoundedListFeature,
  FileCounterFeature,
  FileFreqEstimatorFeature,
  FileKVStore,
  FileMapFeature,
  FilePeriodicCounterFeature,
  FilePersistence,
  FileScalarFeature,
  FileStatsEstimatorFeature
}
import ai.metarank.fstore.file.client.FileClient.{KeyVal, NumCodec}
import ai.metarank.fstore.redis.{
  RedisBoundedListFeature,
  RedisCounterFeature,
  RedisFreqEstimatorFeature,
  RedisKVStore,
  RedisMapFeature,
  RedisPeriodicCounterFeature,
  RedisPersistence,
  RedisScalarFeature,
  RedisStatsEstimatorFeature
}
import ai.metarank.fstore.transfer.StateSink.TransferResult
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.{FeatureKey, FeatureValue, Key, Schema}
import ai.metarank.model.State.{
  BoundedListState,
  CounterState,
  FreqEstimatorState,
  MapState,
  PeriodicCounterState,
  ScalarState,
  StatsEstimatorState
}
import ai.metarank.util.Logging
import cats.effect.IO
import fs2.Stream
import cats.implicits._

import scala.annotation.tailrec

object FileRedisTransfer extends Logging {
  def copy(source: FilePersistence, dest: RedisPersistence) = for {
    _ <- copyGroup[CounterState, FileCounterFeature, RedisCounterFeature](source.counters, dest.counters)
    _ <- copyGroup[PeriodicCounterState, FilePeriodicCounterFeature, RedisPeriodicCounterFeature](
      source.periodicCounters,
      dest.periodicCounters
    )
    _ <- copyGroup[BoundedListState, FileBoundedListFeature, RedisBoundedListFeature](
      source.lists,
      dest.lists
    )
    _ <- copyGroup[FreqEstimatorState, FileFreqEstimatorFeature, RedisFreqEstimatorFeature](
      source.freqs,
      dest.freqs
    )
    _ <- copyGroup[ScalarState, FileScalarFeature, RedisScalarFeature](source.scalars, dest.scalars)
    _ <- copyGroup[StatsEstimatorState, FileStatsEstimatorFeature, RedisStatsEstimatorFeature](
      source.stats,
      dest.stats
    )
    _ <- copyGroup[MapState, FileMapFeature, RedisMapFeature](source.maps, dest.maps)
    _ <- copyOne[FeatureValue, FileKVStore, RedisKVStore[Key, FeatureValue]](source.values, dest.values)
  } yield {
    logger.info("import done")
  }

  def copyGroup[S, F1, F2](sources: Map[FeatureKey, F1], dests: Map[FeatureKey, F2])(implicit
      s: StateSource[S, F1],
      d: StateSink[S, F2]
  ): IO[Unit] = {
    val result = for {
      (name, f1) <- sources.toList
      f2         <- dests.get(name)
    } yield {
      copyOne[S, F1, F2](f1, f2).flatMap(result => info(s"transferred ${name}: ${result.count} records"))
    }
    result.sequence.void
  }

  def copyOne[S, F1, F2](source: F1, dest: F2)(implicit
      s: StateSource[S, F1],
      d: StateSink[S, F2]
  ): IO[TransferResult] = {
    d.sink(dest, s.source(source))
  }
}
