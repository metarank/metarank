package ai.metarank.fstore.file

import ai.metarank.config.StateStoreConfig.FileStateConfig
import ai.metarank.fstore.Persistence
import ai.metarank.fstore.Persistence.ModelName
import ai.metarank.fstore.codec.StoreFormat
import ai.metarank.fstore.file.FilePersistence.{FeatureSize, Prefix}
import ai.metarank.fstore.file.client.FileClient.PrefixSize
import ai.metarank.fstore.file.client.{FileClient, MapDBClient, RocksDBClient}
import ai.metarank.fstore.memory.MemModelStore
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.{Feature, FeatureKey, FeatureValue, Key, Schema}
import cats.effect.IO

import java.nio.file.Path

case class FilePersistence(schema: Schema, db: FileClient, format: StoreFormat) extends Persistence {
  override lazy val counters: Map[FeatureKey, FileCounterFeature] =
    schema.counters.view.mapValues(FileCounterFeature(_, db, Prefix.COUNTER, format)).toMap
  override lazy val periodicCounters: Map[FeatureKey, FilePeriodicCounterFeature] =
    schema.periodicCounters.view.mapValues(FilePeriodicCounterFeature(_, db, Prefix.PCOUNTER, format)).toMap
  override lazy val lists: Map[FeatureKey, FileBoundedListFeature] =
    schema.lists.view.mapValues(FileBoundedListFeature(_, db, Prefix.LIST, format)).toMap
  override lazy val freqs: Map[FeatureKey, FileFreqEstimatorFeature] =
    schema.freqs.view.mapValues(FileFreqEstimatorFeature(_, db, Prefix.FREQ, format)).toMap
  override lazy val scalars: Map[FeatureKey, FileScalarFeature] =
    schema.scalars.view.mapValues(FileScalarFeature(_, db, Prefix.SCALAR, format)).toMap
  override lazy val stats: Map[FeatureKey, FileStatsEstimatorFeature] =
    schema.stats.view.mapValues(FileStatsEstimatorFeature(_, db, Prefix.STATS, format)).toMap
  override lazy val maps: Map[FeatureKey, FileMapFeature] =
    schema.maps.view.mapValues(FileMapFeature(_, db, Prefix.MAP, format)).toMap

  override lazy val models              = MemModelStore()
  override lazy val values: FileKVStore = FileKVStore(db, Prefix.VALUE, format)

  override def healthcheck(): IO[Unit] = IO.unit

  override def sync: IO[Unit] = IO(db.sync())

  def estimateSize(): IO[List[FeatureSize]] = IO.blocking {
    List.concat(
      counters.values.map(f => estimateSize(f.config.name, f.prefix)),
      periodicCounters.values.map(f => estimateSize(f.config.name, f.prefix)),
      lists.values.map(f => estimateSize(f.config.name, f.prefix)),
      freqs.values.map(f => estimateSize(f.config.name, f.prefix)),
      scalars.values.map(f => estimateSize(f.config.name, f.prefix)),
      stats.values.map(f => estimateSize(f.config.name, f.prefix)),
      maps.values.map(f => estimateSize(f.config.name, f.prefix)),
      List(FeatureSize(FeatureName("values"), db.size(Prefix.VALUE.getBytes())))
    )
  }

  def estimateSize(name: FeatureName, prefix: String) =
    FeatureSize(name, db.size(s"${prefix}/${name.value}".getBytes()))
}

object FilePersistence {
  case class FeatureSize(name: FeatureName, size: PrefixSize)
  object Prefix {
    val COUNTER  = "c"
    val PCOUNTER = "pc"
    val LIST     = "l"
    val FREQ     = "f"
    val SCALAR   = "s"
    val STATS    = "ss"
    val MAP      = "m"
    val VALUE    = "v"
  }
  def create(conf: FileStateConfig, schema: Schema) = conf.backend match {
    case FileStateConfig.RocksDBBackend =>
      RocksDBClient.create(Path.of(conf.path)).map(c => FilePersistence(schema, c, conf.format))
    case FileStateConfig.MapDBBackend =>
      MapDBClient.create(Path.of(conf.path)).map(c => FilePersistence(schema, c, conf.format))
  }

}
