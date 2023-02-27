package ai.metarank.fstore.file.client

import cats.effect.IO
import cats.effect.kernel.Resource
import org.rocksdb.{BlockBasedTableConfig, CompressionType, Filter, LRUCache, Options, ReadOptions, RocksDB}

import java.nio.file.Path
import java.util
import scala.collection.mutable.ArrayBuffer
/*

case class RocksDBClient(db: RocksDB) extends FileClient {
  override def put(key: Array[Byte], value: Array[Byte]): Unit = {
    db.put(key, value)
  }

  override def put(keys: Array[Array[Byte]], values: Array[Array[Byte]]): Unit = {
    var i = 0
    while (i < keys.length) {
      db.put(keys(i), values(i))
      i += 1
    }
  }
  override def get(key: Array[Byte]): Option[Array[Byte]] = {
    Option(db.get(key))
  }

  override def firstN(prefix: Array[Byte], n: Int): CloseableIterator[FileClient.KeyVal] = {
    new CloseableIterator[KeyVal] {
      lazy val it = {
        val xit = db.newIterator()
        xit.seek(prefix)
        xit
      }
      var cnt    = 0
      var closed = false
      override def nested: Iterator[KeyVal] = new Iterator[KeyVal] {
        override def hasNext: Boolean = {
          !closed && (cnt < n) && it.isValid && KeyVal.hasPrefix(it.key(), prefix)
        }
        override def next(): KeyVal = {
          cnt += 1
          val result = KeyVal(it.key(), it.value())
          it.next()
          result
        }
      }

      override def close(): Unit = {
        closed = true
        it.close()
      }
    }
  }

  override def lastN(prefix: Array[Byte], n: Int): CloseableIterator[FileClient.KeyVal] = {
    val it = db.newIterator()
    it.seekForPrev(nextKey(prefix))
    new CloseableIterator[KeyVal] {
      var cnt = 0

      override def nested: Iterator[KeyVal] = new Iterator[KeyVal] {
        override def hasNext: Boolean = {
          val over  = cnt < n
          val valid = it.isValid
          val pref  = KeyVal.hasPrefix(it.key(), prefix)
          over && valid && pref
        }

        override def next(): KeyVal = {
          cnt += 1
          val result = KeyVal(it.key(), it.value())
          it.prev()
          result
        }
      }

      override def close(): Unit = it.close()
    }

  }

  override def get(keys: Array[Array[Byte]]): Array[Array[Byte]] = {
    val response = db.multiGetAsList(util.Arrays.asList(keys: _*))
    val buf      = new Array[Array[Byte]](keys.length)
    response.toArray(buf)
    buf
  }

  override def del(key: Array[Byte]): Unit = {
    db.delete(key)
  }

  override def close(): Unit = {
    db.close()
  }

  override def sync(): Unit = {
    db.syncWal()
  }

}

object RocksDBClient {

  def create(path: Path) = Resource.make(IO(createUnsafe(path)))(x => IO(x.close()))
  def createUnsafe(path: Path) = {
    RocksDB.loadLibrary()
    val table = new BlockBasedTableConfig()
    table.setBlockCache(new LRUCache(128 * 1024 * 1024))
    table.setCacheIndexAndFilterBlocks(true)
    table.setBlockSize(1024)
    val opts = new Options()
    opts.setCreateIfMissing(true)
    opts.setOptimizeFiltersForHits(true)
    opts.setTableFormatConfig(table)
    val db = RocksDB.open(opts, path.toString)
    RocksDBClient(db)
  }
}
 */
