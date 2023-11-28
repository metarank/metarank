package ai.metarank.fstore.file.client.rocksdb

import ai.metarank.fstore.file.client.rocksdb.RocksDB.Codec
import ai.metarank.fstore.file.client.{CloseableIterator, DB, SortedDB}
import org.rocksdb.{ReadOptions, RocksDB => RDB}

case class RocksSortedDB[T](db: RDB, codec: Codec[T]) extends RocksDB[T] with SortedDB[T] {
  override def firstN(prefix: String, n: Int): Iterator[(String, T)] = {
    val prefixBytes = prefix.getBytes
    new CloseableIterator[(String, T)] {
      lazy val it = {
        val xit = db.newIterator()
        xit.seek(prefix.getBytes())
        xit
      }
      var cnt    = 0
      var closed = false

      override def nested: Iterator[(String, T)] = new Iterator[(String, T)] {

        override def hasNext: Boolean = {
          !closed && (cnt < n) && it.isValid && hasPrefix(it.key(), prefixBytes)
        }

        override def next(): (String, T) = {
          cnt += 1
          val result = (new String(it.key()), codec.decode(it.value()))
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

  override def lastN(prefix: String, n: Int): Iterator[(String, T)] = {
    val prefixBytes = prefix.getBytes
    val it          = db.newIterator()
    it.seekForPrev(SortedDB.nextKey(prefix).getBytes())
    new CloseableIterator[(String, T)] {
      var cnt    = 0
      var closed = false

      override def nested: Iterator[(String, T)] = new Iterator[(String, T)] {
        override def hasNext: Boolean = {
          val over = cnt < n
          if (!closed) {
            val valid = it.isValid
            val pref  = hasPrefix(it.key(), prefixBytes)
            over && valid && pref
          } else {
            false
          }
        }

        override def next(): (String, T) = {
          cnt += 1
          val result = (new String(it.key()), codec.decode(it.value()))
          it.prev()
          result
        }
      }

      override def close(): Unit = {
        closed = true
        it.close()
      }
    }
  }

  def hasPrefix(key: Array[Byte], prefix: Array[Byte]): Boolean = {
    var i       = 0
    var matches = prefix.length < key.length
    while (matches && (i < prefix.length)) {
      matches = prefix(i) == key(i)
      i += 1
    }
    matches
  }
}
