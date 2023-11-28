package ai.metarank.fstore.file

import ai.metarank.config.StateStoreConfig.FileStateConfig.MapDBBackend
import ai.metarank.fstore.FeatureSuite
import ai.metarank.fstore.file.client.{FileClient, MapDBClient}
import ai.metarank.model.{Feature, FeatureValue, State, Write}
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.scalatest.BeforeAndAfterAll

import java.nio.file.{Files, Path, Paths}
import cats.implicits._

trait FileTest {
  lazy val db: FileClient = MapDBClient.createUnsafe(Files.createTempDirectory("boop"), MapDBBackend())

}
