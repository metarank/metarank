package ai.metarank.fstore.file

import ai.metarank.config.CoreConfig.ImportCacheConfig
import ai.metarank.config.StateStoreConfig.FileStateConfig
import ai.metarank.fstore.Persistence
import ai.metarank.fstore.Persistence.KVStore
import ai.metarank.fstore.cache.{CachedKVStore, NegCachedKVStore}
import ai.metarank.fstore.codec.StoreFormat
import ai.metarank.fstore.file.FilePersistence.FeatureSize
import ai.metarank.fstore.file.client.FileClient.PrefixSize
import ai.metarank.fstore.file.client.{FileClient, MapDBClient}
import ai.metarank.fstore.memory.{MemKVStore, MemModelStore, MemPeriodicCounter}
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.{Feature, FeatureKey, FeatureValue, Key, Schema}
import cats.effect.{IO, Resource}
import com.github.blemale.scaffeine.Scaffeine

import java.nio.file.Path

case class FilePersistence(schema: Schema, db: FileClient, format: StoreFormat, cache: ImportCacheConfig)
    extends Persistence {
  override lazy val counters: Map[FeatureKey, FileCounterFeature] =
    schema.counters.view.mapValues(c => FileCounterFeature(c, db.sortedIntDB(c.name.value), format)).toMap
  override lazy val periodicCounters: Map[FeatureKey, FilePeriodicCounterFeature] =
    schema.periodicCounters.view
      .mapValues(c => FilePeriodicCounterFeature(c, db.sortedIntDB(c.name.value), format))
      .toMap
  override lazy val lists: Map[FeatureKey, FileBoundedListFeature] =
    schema.lists.view.mapValues(c => FileBoundedListFeature(c, db.sortedDB(c.name.value), format)).toMap
  override lazy val freqs: Map[FeatureKey, FileFreqEstimatorFeature] =
    schema.freqs.view.mapValues(c => FileFreqEstimatorFeature(c, db.sortedStringDB(c.name.value), format)).toMap
  override lazy val scalars: Map[FeatureKey, FileScalarFeature] =
    schema.scalars.view.mapValues(c => FileScalarFeature(c, db.hashDB(c.name.value), format)).toMap
  override lazy val stats: Map[FeatureKey, FileStatsEstimatorFeature] =
    schema.stats.view.mapValues(c => FileStatsEstimatorFeature(c, db.sortedFloatDB(c.name.value), format)).toMap
  override lazy val maps: Map[FeatureKey, FileMapFeature] =
    schema.maps.view.mapValues(c => FileMapFeature(c, db.sortedDB(c.name.value), format)).toMap

  override lazy val models = MemModelStore()
  lazy val fileValues      = FileKVStore(db.hashDB("values"), format)
  override lazy val values: KVStore[Key, FeatureValue] = if (cache.enabled) {
    NegCachedKVStore(
      cache = Scaffeine().maximumSize(cache.size).recordStats().softValues().build[Key, Option[FeatureValue]](),
      slow = fileValues
    )
  } else {
    fileValues
  }

  override def healthcheck(): IO[Unit] = IO.unit

  override def sync: IO[Unit] = IO.unit

  def estimateSize(): IO[List[FeatureSize]] = IO.blocking {
    List.concat(
      counters.values.map(f => FeatureSize(f.config.name, f.db.size())),
      periodicCounters.values.map(f => FeatureSize(f.config.name, f.db.size())),
      lists.values.map(f => FeatureSize(f.config.name, f.db.size())),
      freqs.values.map(f => FeatureSize(f.config.name, f.db.size())),
      scalars.values.map(f => FeatureSize(f.config.name, f.db.size())),
      stats.values.map(f => FeatureSize(f.config.name, f.db.size())),
      maps.values.map(f => FeatureSize(f.config.name, f.db.size())),
      List(FeatureSize(FeatureName("values"), fileValues.db.size()))
    )
  }

}

object FilePersistence {
  case class FeatureSize(name: FeatureName, size: PrefixSize)
  def create(conf: FileStateConfig, schema: Schema, imp: ImportCacheConfig): Resource[IO, FilePersistence] =
    conf.backend match {
      case FileStateConfig.RocksDBBackend =>
        Resource.raiseError[IO, FilePersistence, Throwable](new Exception("not yet implemented"))
      case FileStateConfig.MapDBBackend =>
        MapDBClient.create(Path.of(conf.path)).map(c => FilePersistence(schema, c, conf.format, imp))
    }

}
