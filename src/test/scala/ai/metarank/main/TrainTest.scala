package ai.metarank.main

import ai.metarank.config.CoreConfig
import ai.metarank.config.CoreConfig.ClickthroughJoinConfig
import ai.metarank.flow.ClickthroughJoinBuffer
import ai.metarank.fstore.memory.{MemClickthroughStore, MemPersistence}
import ai.metarank.main.command.{Import, Train}
import ai.metarank.model.Timestamp
import ai.metarank.rank.LambdaMARTModel
import ai.metarank.util.RandomDataset
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.jdk.StreamConverters._
import java.nio.file.Files

class TrainTest extends AnyFlatSpec with Matchers {
  lazy val dataset = RandomDataset.generate(1000)
  lazy val store   = MemPersistence(dataset.mapping.schema)
  lazy val cs      = MemClickthroughStore()
  lazy val buffer  = ClickthroughJoinBuffer(ClickthroughJoinConfig(), store.values, cs, dataset.mapping)

  it should "generate test data" in {
    Import.slurp(fs2.Stream.emits(dataset.events), store, dataset.mapping, buffer).unsafeRunSync()
    buffer.flushQueue(Timestamp.max).unsafeRunSync()
  }

  it should "train xgboost model" in {
    val result = train("xgboost")
    result.iterations.size shouldBe 10
    result.features.size shouldBe 2
  }

  it should "train xgboost model with a feature subset" in {
    val result = train("xgboost1")
    result.iterations.size shouldBe 10
    result.features.size shouldBe 1
  }

  it should "train lightgbm model" in {
    val result = train("lightgbm")
    result.iterations.size shouldBe 10
    result.features.size shouldBe 2
  }

  it should "export training data" in {
    val path  = Files.createTempDirectory("export")
    val model = dataset.mapping.models("xgboost").asInstanceOf[LambdaMARTModel]
    Train.train(store, cs, model, "xgboost", model.conf.backend, Some(path)).unsafeRunSync()
    val children = Files.list(path).toScala(List)
    children.map(_.getFileName.toString).sorted shouldBe List("test.csv", "train.csv")
  }

  def train(name: String) = {
    val model = dataset.mapping.models(name).asInstanceOf[LambdaMARTModel]
    Train.train(store, cs, model, "xgboost", model.conf.backend, None).unsafeRunSync()
  }
}
