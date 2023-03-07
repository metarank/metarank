package ai.metarank.fstore.file.client.mapdb

import ai.metarank.fstore.file.client.{FileClient, SortedDB}
import org.mapdb.BTreeMap

import scala.jdk.CollectionConverters._

case class MapdbSortedDB[T](tree: BTreeMap[String, T], sz: T => Int) extends SortedDB[T] {
  import SortedDB._
  import FileClient._

  override def get(key: String): Option[T]      = Option(tree.get(key))
  override def put(key: String, value: T): Unit = tree.put(key, value)
  override def del(key: String): Unit           = tree.remove(key)
  override def close(): Unit                    = tree.close()

  def firstN(prefix: String, n: Int): Iterator[(String, T)] = {
    tree.entryIterator(prefix, true, nextKey(prefix), false).asScala.map(e => (e.getKey, e.getValue)).take(n)
  }

  def lastN(prefix: String, n: Int): Iterator[(String, T)] = {
    tree
      .descendingEntryIterator(prefix, true, nextKey(prefix), false)
      .asScala
      .map(e => (e.getKey, e.getValue))
      .take(n)
  }

  override def all(): Iterator[(String, T)] = tree.entryIterator().asScala.map(e => e.getKey -> e.getValue)

  override def sizeof(value: T): Int = sz(value)
}
