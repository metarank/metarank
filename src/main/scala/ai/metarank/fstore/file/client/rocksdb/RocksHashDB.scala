package ai.metarank.fstore.file.client.rocksdb

import ai.metarank.fstore.file.client.HashDB
import ai.metarank.fstore.file.client.rocksdb.RocksDB.Codec
import com.github.blemale.scaffeine.{Cache, Scaffeine}
import org.rocksdb.{RocksDB => RDB}

import scala.collection.mutable.ArrayBuffer
import scala.jdk.CollectionConverters._

case class RocksHashDB(db: RDB) extends RocksDB[Array[Byte]] with HashDB[Array[Byte]] {
  lazy val codec = Codec.BYTES
  override def get(keys: Array[String]): Array[Array[Byte]] = {
    db.multiGetAsList(keys.toList.map(_.getBytes()).asJava).asScala.toArray
  }

  override def put(keys: Array[String], values: Array[Array[Byte]]): Unit = {
    var i = 0
    while (i < keys.length) {
      put(keys(i), values(i))
      i += 1
    }
  }
}
