package ai.metarank.fstore.file

import ai.metarank.config.StateStoreConfig.FileStateConfig
import ai.metarank.fstore.Persistence
import ai.metarank.fstore.Persistence.ModelName
import ai.metarank.fstore.codec.StoreFormat
import ai.metarank.fstore.file.client.{FileClient, MapDBClient, RocksDBClient}
import ai.metarank.fstore.memory.MemModelStore
import ai.metarank.model.{FeatureKey, FeatureValue, Key, Schema}
import cats.effect.IO

import java.nio.file.Path

case class FilePersistence(schema: Schema, db: FileClient, format: StoreFormat) extends Persistence {
  override lazy val counters: Map[FeatureKey, FileCounterFeature] =
    schema.counters.view.mapValues(FileCounterFeature(_, db, "c", format)).toMap
  override lazy val periodicCounters: Map[FeatureKey, FilePeriodicCounterFeature] =
    schema.periodicCounters.view.mapValues(FilePeriodicCounterFeature(_, db, "pc", format)).toMap
  override lazy val lists: Map[FeatureKey, FileBoundedListFeature] =
    schema.lists.view.mapValues(FileBoundedListFeature(_, db, "l", format)).toMap
  override lazy val freqs: Map[FeatureKey, FileFreqEstimatorFeature] =
    schema.freqs.view.mapValues(FileFreqEstimatorFeature(_, db, "f", format)).toMap
  override lazy val scalars: Map[FeatureKey, FileScalarFeature] =
    schema.scalars.view.mapValues(FileScalarFeature(_, db, "s", format)).toMap
  override lazy val stats: Map[FeatureKey, FileStatsEstimatorFeature] =
    schema.stats.view.mapValues(FileStatsEstimatorFeature(_, db, "ss", format)).toMap
  override lazy val maps: Map[FeatureKey, FileMapFeature] =
    schema.maps.view.mapValues(FileMapFeature(_, db, "m", format)).toMap

  override lazy val models              = MemModelStore()
  override lazy val values: FileKVStore = FileKVStore(db, "v", format)

  override def healthcheck(): IO[Unit] = IO.unit

  override def sync: IO[Unit] = IO(db.sync())
}

object FilePersistence {
  def create(conf: FileStateConfig, schema: Schema) = conf.backend match {
    case FileStateConfig.RocksDBBackend =>
      RocksDBClient.create(Path.of(conf.path)).map(c => FilePersistence(schema, c, conf.format))
    case FileStateConfig.MapDBBackend =>
      MapDBClient.create(Path.of(conf.path)).map(c => FilePersistence(schema, c, conf.format))
  }

}
