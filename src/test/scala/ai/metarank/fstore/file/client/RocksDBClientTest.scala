package ai.metarank.fstore.file.client

import java.nio.file.Files
import scala.util.Random

class RocksDBClientTest extends FileTestSuite {
  lazy val rdb = RocksDBClient("/tmp")

  override def makeHash(): HashDB[Array[Byte]] = rdb.hashDB("yep" + Random.nextInt())

  override def makeTree(): SortedDB[String] = rdb.sortedStringDB("baa" + Random.nextInt())

  it should "reopen the same state file" in {
    val path = Files.createTempDirectory("tmp")
    val db1 = MapDBClient.createUnsafe(path)
    val kv1 = db1.hashDB("test")
    kv1.put("foo", "bar".getBytes())
    db1.close()

    val db2 = MapDBClient.createUnsafe(path)
    val kv2 = db2.hashDB("test")
    kv2.get("foo").map(b => new String(b)) shouldBe Some("bar")
  }

}
