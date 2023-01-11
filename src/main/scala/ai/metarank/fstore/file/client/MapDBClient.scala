package ai.metarank.fstore.file.client

import ai.metarank.fstore.file.client.FileClient.KeyVal
import cats.effect.{IO, Resource}
import org.mapdb.serializer.GroupSerializer
import org.mapdb.{BTreeMap, DB, DBMaker, HTreeMap, Serializer}

import java.nio.file.{Files, Path, Paths}
import scala.jdk.CollectionConverters._

class MapDBClient(db: DB, tree: BTreeMap[Array[Byte], Array[Byte]]) extends FileClient {
  def put(key: Array[Byte], value: Array[Byte]): Unit = {
    tree.put(key, value)
  }

  def put(keys: Array[Array[Byte]], values: Array[Array[Byte]]) = {
    keys.zip(values).map { case (k, v) =>
      tree.put(k, v)
    }
  }

  def get(key: Array[Byte]): Option[Array[Byte]] = {
    Option(tree.get(key))
  }

  def get(keys: Array[Array[Byte]]): Array[Array[Byte]] = {
    keys.map(k => tree.get(k))
  }

  def del(key: Array[Byte]): Unit = {
    tree.remove(key)
  }

  def firstN(prefix: Array[Byte], n: Int): Iterator[KeyVal] = {
    tree.entryIterator(prefix, true, nextKey(prefix), false).asScala.map(e => KeyVal(e.getKey, e.getValue)).take(n)
  }

  def lastN(prefix: Array[Byte], n: Int): Iterator[KeyVal] = {
    tree
      .descendingEntryIterator(prefix, true, nextKey(prefix), false)
      .asScala
      .map(e => KeyVal(e.getKey, e.getValue))
      .take(n)
  }

  def close(): Unit = {
    tree.close()
    db.close()
  }

  def sync(): Unit = {}
}

object MapDBClient {
  def create(path: Path): Resource[IO, MapDBClient] = Resource.make(IO(createUnsafe(path)))(m => IO(m.close()))

  def createUnsafe(path: Path) = {
    val db = DBMaker.fileDB(path.toString + "/state.db").fileMmapEnable().closeOnJvmShutdown().make()
    val tree =
      db.treeMap("tree", Serializer.BYTE_ARRAY, Serializer.BYTE_ARRAY)
        .valuesOutsideNodesEnable
        .maxNodeSize(16)
        .create()
    new MapDBClient(db, tree)
  }
}
