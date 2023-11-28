package ai.metarank.fstore.file.client.rocksdb
import ai.metarank.fstore.file.client.{CloseableIterator, DB, HashDB}
import ai.metarank.fstore.file.client.rocksdb.RocksDB.Codec
import org.rocksdb.{ReadOptions, RocksDB => RDB}

import java.nio.ByteBuffer

trait RocksDB[V] extends DB[V] {
  def db: RDB
  def codec: Codec[V]
  override def put(key: String, value: V): Unit = {
    db.put(key.getBytes(), codec.encode(value))
  }

  override def get(key: String): Option[V] = {
    Option(db.get(key.getBytes())).map(codec.decode)
  }

  override def del(key: String): Unit = {
    db.delete(key.getBytes())
  }

  override def close(): Unit = {
    db.close()
  }

  override def all(): Iterator[(String, V)] = {
    val opts = new ReadOptions()
    val rit  = db.newIterator(opts)
    rit.seekToFirst()
    new CloseableIterator[(String, V)] {
      var closed = false
      override def nested: Iterator[(String, V)] = new Iterator[(String, V)] {
        override def hasNext: Boolean = {
          val br = 1
          !closed && rit.isValid
        }

        override def next(): (String, V) = {
          val br = 1
          val k  = new String(rit.key())
          val v  = codec.decode(rit.value())
          rit.next()
          (k, v)
        }
      }

      override def close(): Unit = {
        val b = 1
        rit.close()
        closed = true
      }
    }
  }

  override def sync(): Unit = {
    db.syncWal()
  }

  override def sizeof(value: V): Int = 4 + codec.encode(value).length

}

object RocksDB {
  sealed trait Codec[T] {
    def encode(value: T): Array[Byte]
    def decode(value: Array[Byte]): T
  }

  object Codec {
    val INT = new Codec[Int] {
      override def decode(value: Array[Byte]): Int = ByteBuffer.wrap(value).getInt
      override def encode(value: Int): Array[Byte] = ByteBuffer.allocate(4).putInt(value).array()
    }
    val FLOAT = new Codec[Float] {
      override def decode(value: Array[Byte]): Float = ByteBuffer.wrap(value).getFloat
      override def encode(value: Float): Array[Byte] = ByteBuffer.allocate(4).putFloat(value).array()
    }

    val BYTES = new Codec[Array[Byte]] {
      override def decode(value: Array[Byte]): Array[Byte] = value
      override def encode(value: Array[Byte]): Array[Byte] = value
    }

    val STRING = new Codec[String] {
      override def decode(value: Array[Byte]): String = new String(value)
      override def encode(value: String): Array[Byte] = value.getBytes()
    }
  }
}
