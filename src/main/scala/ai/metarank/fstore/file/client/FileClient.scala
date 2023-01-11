package ai.metarank.fstore.file.client

import ai.metarank.fstore.file.client.FileClient.{KeyVal, NumCodec}

import java.nio.{BufferOverflowException, ByteBuffer}
import java.util
import scala.annotation.tailrec

trait FileClient {
  def put(key: Array[Byte], value: Array[Byte]): Unit
  def put(keys: Array[Array[Byte]], values: Array[Array[Byte]]): Unit
  def get(key: Array[Byte]): Option[Array[Byte]]
  def get(keys: Array[Array[Byte]]): Array[Array[Byte]]
  def del(key: Array[Byte]): Unit
  def firstN(prefix: Array[Byte], n: Int): Iterator[KeyVal]
  def lastN(prefix: Array[Byte], n: Int): Iterator[KeyVal]
  def close(): Unit
  def sync(): Unit

  def inc(key: Array[Byte], i: Int): Unit = {
    val value = get(key) match {
      case Some(buf) => NumCodec.readInt(buf) + i
      case None      => i
    }
    put(key, NumCodec.writeInt(value))
  }

  def put(key: Array[Byte], value: Int): Unit = {
    put(key, NumCodec.writeInt(value))
  }
  def put(key: Array[Byte], value: Double): Unit = {
    put(key, NumCodec.writeDouble(value))
  }
  def getInt(key: Array[Byte]): Option[Int] = {
    get(key).map(NumCodec.readInt)
  }

  def getDouble(key: Array[Byte]): Option[Double] = {
    get(key).map(NumCodec.readDouble)
  }

  def nextKey(key: Array[Byte]): Array[Byte] = {
    val copy = util.Arrays.copyOf(key, key.length)
    nextKey(copy, key.length - 1)
  }

  @tailrec private def nextKey(key: Array[Byte], index: Int): Array[Byte] = {
    if (key(index) != Byte.MaxValue) {
      key(index) = (key(index) + 1).toByte
      key
    } else {
      key(index) = Byte.MinValue
      if (index == 0) {
        throw new BufferOverflowException()
      } else {
        nextKey(key, index - 1)
      }
    }
  }
}

object FileClient {
  object NumCodec {
    def writeInt(i: Int): Array[Byte] = {
      val buf = new Array[Byte](4)
      ByteBuffer.wrap(buf).putInt(i).array()
    }
    def readInt(buf: Array[Byte]): Int = {
      ByteBuffer.wrap(buf).getInt
    }
    def writeDouble(d: Double): Array[Byte] = {
      val buf = new Array[Byte](8)
      ByteBuffer.wrap(buf).putDouble(d).array()
    }
    def readDouble(buf: Array[Byte]): Double = {
      ByteBuffer.wrap(buf).getDouble
    }
  }
  case class KeyVal(key: Array[Byte], value: Array[Byte]) {
    override def toString: String      = s"KeyVal(${new String(key)},${new String(value)}"
    def hasPrefix(prefix: Array[Byte]) = KeyVal.hasPrefix(key, prefix)
  }

  object KeyVal {
    def hasPrefix(key: Array[Byte], prefix: Array[Byte]) = {
      if (prefix.length > key.length) {
        false
      } else {
        util.Arrays.compare(prefix, 0, prefix.length, key, 0, prefix.length) == 0
      }
    }
  }
}
