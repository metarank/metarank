package ai.metarank.fstore.file.client

import ai.metarank.config.StateStoreConfig.FileStateConfig.MapDBBackend

import java.nio.file.{Files, Paths}
import scala.util.Random

class MapDBClientTest extends FileTestSuite {
  lazy val mapdb = MapDBClient.createUnsafe(Files.createTempDirectory("tmp"), MapDBBackend())
  override def makeHash(): HashDB[Array[Byte]] = mapdb.hashDB("yep" + Random.nextInt())

  override def makeTree(): SortedDB[String] = mapdb.sortedStringDB("baa" + Random.nextInt())

  it should "reopen the same state file" in {
    val path = Files.createTempDirectory("tmp")
    val db1  = MapDBClient.createUnsafe(path, MapDBBackend())
    val kv1  = db1.hashDB("test")
    kv1.put("foo", "bar".getBytes())
    db1.close()

    val db2 = MapDBClient.createUnsafe(path, MapDBBackend())
    val kv2 = db2.hashDB("test")
    kv2.get("foo").map(b => new String(b)) shouldBe Some("bar")
  }
}
