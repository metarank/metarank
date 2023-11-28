package ai.metarank.fstore.file.client

import ai.metarank.config.StateStoreConfig.FileStateConfig.MapDBBackend
import ai.metarank.fstore.file.client.mapdb.{MapdbHashDB, MapdbSortedDB, ScalaFloatSerializer, ScalaIntSerializer}
import cats.effect.{IO, Resource}
import org.mapdb.{BTreeMap, DB, DBMaker, HTreeMap, Serializer}

import java.nio.file.{Files, Path, Paths}

class MapDBClient(db: DB) extends FileClient {
  override def hashDB(name: String): HashDB[Array[Byte]] = {
    val hash = db.hashMap(name, Serializer.STRING, Serializer.BYTE_ARRAY).createOrOpen()
    MapdbHashDB(hash)
  }

  override def sortedStringDB(name: String): SortedDB[String] = {
    val tree = db.treeMap(name, Serializer.STRING, Serializer.STRING).maxNodeSize(16).createOrOpen()
    MapdbSortedDB(tree, _.length)
  }

  override def sortedDB(name: String): SortedDB[Array[Byte]] = {
    val tree = db
      .treeMap(name, Serializer.STRING, Serializer.BYTE_ARRAY)
      .maxNodeSize(16)
      .valuesOutsideNodesEnable()
      .createOrOpen()
    MapdbSortedDB(tree, _.length)
  }

  override def sortedFloatDB(name: String): SortedDB[Float] = {
    val tree = db
      .treeMap(name, Serializer.STRING, ScalaFloatSerializer)
      .maxNodeSize(16)
      .valuesOutsideNodesEnable()
      .createOrOpen()
    MapdbSortedDB(tree, _ => 4)
  }

  override def sortedIntDB(name: String): SortedDB[Int] = {
    val tree =
      db.treeMap(name, Serializer.STRING, ScalaIntSerializer).maxNodeSize(16).createOrOpen()
    MapdbSortedDB(tree, _ => 4)
  }

  def close() =
    db.close()

}

object MapDBClient {
  def create(path: Path, opts: MapDBBackend): Resource[IO, MapDBClient] = Resource.make(IO(createUnsafe(path)))(m => IO(m.close()))

  def createUnsafe(path: Path) = {
    val pathFile = path.toFile
    if (!pathFile.exists()) {
      pathFile.mkdirs()
    }
    val db = DBMaker.fileDB(path.toString + "/state.db").fileMmapEnable().closeOnJvmShutdown().make()
    new MapDBClient(db)
  }
}
