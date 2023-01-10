package ai.metarank.fstore.file.client

import java.nio.file.{Files, Paths}

class MapDBClientTest extends FileTestSuite {
  override def make(): FileClient = MapDBClient.createUnsafe(Files.createTempDirectory("tmp"))
}
