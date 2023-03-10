package ai.metarank.main

import ai.metarank.config.CoreConfig.ClickthroughJoinConfig
import ai.metarank.flow.ClickthroughJoinBuffer
import ai.metarank.fstore.memory.{MemTrainStore, MemPersistence}
import ai.metarank.main.command.train.SplitStrategy
import ai.metarank.main.command.{Export, Import}
import ai.metarank.model.Timestamp
import ai.metarank.util.{RandomDataset, TestConfig}
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.jdk.StreamConverters._
import java.nio.file.Files

class ExportTest extends AnyFlatSpec with Matchers {
  lazy val dataset = RandomDataset.generate(1000)
  lazy val store   = MemPersistence(dataset.mapping.schema)
  lazy val cs      = MemTrainStore()
  lazy val buffer  = ClickthroughJoinBuffer(ClickthroughJoinConfig(), store.values, cs, dataset.mapping)

  it should "generate test data" in {
    Import.slurp(fs2.Stream.emits(dataset.events), store, dataset.mapping, buffer, TestConfig()).unsafeRunSync()
    buffer.flushAll().unsafeRunSync()
  }

  it should "export training data" in {
    val path = Files.createTempDirectory("export")
    Export.doexport(cs, dataset.mapping, "xgboost", path, 1.0, SplitStrategy.default).unsafeRunSync()
    val children = Files.list(path).toScala(List)
    children.map(_.getFileName.toString).sorted shouldBe List("test.svm", "train.svm", "xgboost.conf")
  }

  it should "export sampled training data" in {
    val path = Files.createTempDirectory("export")
    Export.doexport(cs, dataset.mapping, "xgboost", path, 0.1, SplitStrategy.default).unsafeRunSync()
    val children = Files.list(path).toScala(List)
    children.map(_.getFileName.toString).sorted shouldBe List("test.svm", "train.svm", "xgboost.conf")
  }

}
