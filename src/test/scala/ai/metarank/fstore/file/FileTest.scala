package ai.metarank.fstore.file

import ai.metarank.config.StateStoreConfig.FileStateConfig.MapDBBackend
import ai.metarank.fstore.file.client.{FileClient, MapDBClient}

import java.nio.file.Files

trait FileTest {
  lazy val db: FileClient = MapDBClient.createUnsafe(Files.createTempDirectory("boop"), MapDBBackend())

}
