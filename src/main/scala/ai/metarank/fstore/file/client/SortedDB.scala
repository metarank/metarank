package ai.metarank.fstore.file.client

import java.nio.BufferOverflowException
import scala.annotation.tailrec

trait SortedDB[V] extends DB[V] {
  def firstN(prefix: String, n: Int): Iterator[(String, V)]
  def lastN(prefix: String, n: Int): Iterator[(String, V)]
  def all(): Iterator[(String, V)]
}

object SortedDB {
  def nextKey(key: String): String = {
    val keyBytes = key.getBytes()
    val mod      = nextKey(keyBytes, keyBytes.length - 1)
    new String(mod)
  }

  @tailrec final def nextKey(key: Array[Byte], index: Int): Array[Byte] = {
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
