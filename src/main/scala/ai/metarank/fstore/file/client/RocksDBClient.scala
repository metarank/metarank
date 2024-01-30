package ai.metarank.fstore.file.client

import ai.metarank.config.StateStoreConfig.FileStateConfig.RocksDBBackend
import ai.metarank.fstore.file.client.rocksdb.RocksDB.Codec
import ai.metarank.fstore.file.client.rocksdb.{RocksHashDB, RocksSortedDB}
import ai.metarank.util.Logging
import cats.effect.IO
import cats.effect.kernel.Resource
import fs2.io.file.{Files, Path}
import org.rocksdb.{BlockBasedTableConfig, CompressionType, Filter, LRUCache, Options, ReadOptions, RocksDB}

import java.util
import scala.collection.mutable.ArrayBuffer
import org.rocksdb.{RocksDB => RDB}

import java.io.File

case class RocksDBClient(dir: String, opts: RocksDBBackend) extends FileClient with Logging {
  val options = {
    val o = new Options()
    o.setCreateIfMissing(true)
    val table = new BlockBasedTableConfig()
    table.setBlockCache(new LRUCache(opts.lruCacheSize))
    table.setCacheIndexAndFilterBlocks(true)
    table.setBlockSize(opts.blockSize)
    o.setTableFormatConfig(table)
    o
  }

  val dbs = ArrayBuffer[RDB]()

  override def hashDB(name: String): HashDB[Array[Byte]] = {
    val db = RDB.open(options, List(dir, name).mkString(File.separator))
    dbs.addOne(db)
    RocksHashDB(db)
  }

  override def sortedDB(name: String): SortedDB[Array[Byte]] = {
    val db = RDB.open(options, List(dir, name).mkString(File.separator))
    dbs.addOne(db)
    RocksSortedDB(db, Codec.BYTES)
  }

  override def sortedFloatDB(name: String): SortedDB[Float] = {
    val db = RDB.open(options, List(dir, name).mkString(File.separator))
    dbs.addOne(db)
    RocksSortedDB(db, Codec.FLOAT)
  }

  override def sortedIntDB(name: String): SortedDB[Int] = {
    val db = RDB.open(options, List(dir, name).mkString(File.separator))
    dbs.addOne(db)
    RocksSortedDB(db, Codec.INT)
  }

  override def sortedStringDB(name: String): SortedDB[String] = {
    val db = RDB.open(options, List(dir, name).mkString(File.separator))
    dbs.addOne(db)
    RocksSortedDB(db, Codec.STRING)
  }

  override def compact(): Unit = {
    dbs.foreach(db => {
      logger.info(s"triggering compaction for ${db.getName}")
      db.compactRange()
    })
  }

  override def close(): Unit = {
    dbs.foreach(_.close())
  }
}

object RocksDBClient extends Logging {
  def create(path: java.nio.file.Path, opts: RocksDBBackend) = Resource.make(for {
    exists <- Files[IO].exists(fs2.io.file.Path(path.toString))
    _ <- IO.whenA(!exists)(
      Files[IO].createDirectory(fs2.io.file.Path(path.toString)) *> info(s"created rocksdb dir $path")
    )
    c <- IO(createUnsafe(path, opts))
  } yield c)(x => IO(x.close()))

  def createUnsafe(path: java.nio.file.Path, opts: RocksDBBackend) = {
    RocksDBClient(path.toString, opts)
  }
}
