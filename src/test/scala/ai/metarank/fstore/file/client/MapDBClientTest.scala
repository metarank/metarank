package ai.metarank.fstore.file.client

import java.nio.file.{Files, Paths}
import scala.util.Random

class MapDBClientTest extends FileTestSuite {
  lazy val mapdb                  = MapDBClient.createUnsafe(Files.createTempDirectory("tmp"))
  override def makeHash(): HashDB = mapdb.hashDB("yep" + Random.nextInt())

  override def makeTree(): SortedDB[String] = mapdb.sortedStringDB("baa" + Random.nextInt())
}
