package ai.metarank.fstore.file.client

trait DB[T] {
  def put(key: String, value: T): Unit
  def get(key: String): Option[T]
  def del(key: String): Unit
  def close(): Unit
  def all(): Iterator[(String, T)]
  def sync(): Unit = {}
  def sizeof(value: T): Int
  def compact(): Unit = {}

  def size(): FileClient.PrefixSize = {
    val it         = all()
    var keyBytes   = 0L
    var valueBytes = 0L
    var count      = 0
    while (it.hasNext) {
      val entry = it.next()
      count += 1
      keyBytes += entry._1.length
      valueBytes += sizeof(entry._2)
    }
    FileClient.PrefixSize(keyBytes, valueBytes, count)
  }

}
