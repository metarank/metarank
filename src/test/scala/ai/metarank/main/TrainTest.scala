package ai.metarank.main

import ai.metarank.config.CoreConfig
import ai.metarank.config.CoreConfig.ClickthroughJoinConfig
import ai.metarank.config.ModelConfig.LambdaMARTConfig
import ai.metarank.config.ModelConfig.ModelBackend.XGBoostBackend
import ai.metarank.config.Selector.FieldSelector
import ai.metarank.flow.ClickthroughJoinBuffer
import ai.metarank.fstore.memory.{MemClickthroughStore, MemPersistence}
import ai.metarank.main.command.train.SplitStrategy
import ai.metarank.main.command.{Import, Train}
import ai.metarank.model.Field.StringField
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.{EventId, Timestamp}
import ai.metarank.rank.LambdaMARTModel
import ai.metarank.util.{RandomDataset, TestClickthroughValues}
import cats.data.NonEmptyList
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
    buffer.flushAll().unsafeRunSync()
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

  it should "select events per model" in {
    val store = MemClickthroughStore()
    val ct    = TestClickthroughValues()
    val ct1   = ct.copy(ct = ct.ct.copy(id = EventId("1"), rankingFields = List(StringField("source", "search"))))
    val ct2   = ct.copy(ct = ct.ct.copy(id = EventId("2"), rankingFields = List(StringField("source", "recs"))))
    val model = dataset.mapping.models.values.collectFirst { case m: LambdaMARTModel =>
      m.copy(conf = m.conf.copy(selector = FieldSelector("source", "search")))
    }.get
    store.put(List(ct1, ct2)).unsafeRunSync()
    val load = Train.loadDataset(store, model).unsafeRunSync()
    load.size shouldBe 1
  }

  def train(name: String) = {
    val model = dataset.mapping.models(name).asInstanceOf[LambdaMARTModel]
    Train.train(store, cs, model, "xgboost", model.conf.backend, SplitStrategy.default).unsafeRunSync()
  }
}
