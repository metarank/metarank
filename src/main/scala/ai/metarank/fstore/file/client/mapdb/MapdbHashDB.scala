package ai.metarank.fstore.file.client.mapdb

import ai.metarank.fstore.file.client.HashDB
import org.mapdb.HTreeMap
import scala.jdk.CollectionConverters._

case class MapdbHashDB(map: HTreeMap[String, Array[Byte]]) extends HashDB[Array[Byte]] {
  def put(key: String, value: Array[Byte]): Unit = {
    map.put(key, value)
  }

  def put(keys: Array[String], values: Array[Array[Byte]]) = {
    keys.zip(values).map { case (k, v) =>
      map.put(k, v)
    }
  }

  def get(key: String): Option[Array[Byte]] = Option(map.get(key))

  def get(keys: Array[String]): Array[Array[Byte]] = keys.map(k => map.get(k))

  def del(key: String): Unit = map.remove(key)

  def close(): Unit = {} // map.close()

  def all() = map.entrySet().iterator().asScala.map(e => e.getKey -> e.getValue)

  override def sizeof(value: Array[Byte]): Int = value.length + 4

}
