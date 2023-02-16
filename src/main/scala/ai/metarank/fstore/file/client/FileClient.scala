package ai.metarank.fstore.file.client

import ai.metarank.fstore.file.client.FileClient.{NumCodec, PrefixSize}

import java.nio.{BufferOverflowException, ByteBuffer}

trait FileClient {
  def sortedDB(name: String): SortedDB[Array[Byte]]
  def sortedStringDB(name: String): SortedDB[String]
  def sortedFloatDB(name: String): SortedDB[Float]
  def sortedIntDB(name: String): SortedDB[Int]
  def hashDB(name: String): HashDB
  def close(): Unit
}

object FileClient {
  case class PrefixSize(keyBytes: Long, valueBytes: Long, count: Int)
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
}
