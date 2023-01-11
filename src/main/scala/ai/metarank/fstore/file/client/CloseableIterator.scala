package ai.metarank.fstore.file.client

trait CloseableIterator[T] extends Iterator[T] {
  def nested: Iterator[T]
  def close(): Unit
  override def hasNext: Boolean = {
    val result = nested.hasNext
    if (!result) close()
    result
  }

  override def next(): T = nested.next()
}
