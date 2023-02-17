package ai.metarank.fstore.file.client

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
}
