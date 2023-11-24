package ai.metarank.fstore.file.client

import ai.metarank.fstore.file.client.rocksdb.RocksDB.Codec
import ai.metarank.fstore.file.client.rocksdb.{RocksHashDB, RocksSortedDB}
import cats.effect.IO
import cats.effect.kernel.Resource
import org.rocksdb.{BlockBasedTableConfig, CompressionType, Filter, LRUCache, Options, ReadOptions, RocksDB}

import java.nio.file.Path
import java.util
import scala.collection.mutable.ArrayBuffer
import org.rocksdb.{RocksDB => RDB}

import java.io.File

case class RocksDBClient(dir: String) extends FileClient {
  val options = {
    val o = new Options()
    o.setCreateIfMissing(true)
    o.setOptimizeFiltersForHits(true)
    val table = new BlockBasedTableConfig()
    table.setBlockCache(new LRUCache(128 * 1024 * 1024))
    table.setCacheIndexAndFilterBlocks(true)
    table.setBlockSize(1024)
    o.setTableFormatConfig(table)
    o
  }

  val dbs = ArrayBuffer[RDB]()

  override def hashDB(name: String): HashDB[Array[Byte]] = {
    val db =   RDB.open(options, List(dir,name).mkString(File.separator))
    dbs.addOne(db)
    RocksHashDB(db)
  }

  override def sortedDB(name: String): SortedDB[Array[Byte]] = {
    val db =   RDB.open(options, List(dir,name).mkString(File.separator))
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

  override def close(): Unit = {
    dbs.foreach(_.close())
  }
}


object RocksDBClient {

  def create(path: Path) = Resource.make(IO(RocksDBClient(path.toString)))(x => IO(x.close()))

}

