package ai.metarank.main

import ai.metarank.config.CoreConfig.ClickthroughJoinConfig
import ai.metarank.config.Selector.FieldSelector
import ai.metarank.flow.ClickthroughJoinBuffer
import ai.metarank.fstore.memory.{MemTrainStore, MemPersistence}
import ai.metarank.main.command.train.SplitStrategy
import ai.metarank.main.command.{Import, Train}
import ai.metarank.ml.rank.LambdaMARTRanker.LambdaMARTPredictor
import ai.metarank.model.Field.StringField
import ai.metarank.model.{EventId, Timestamp}
import ai.metarank.util.{RandomDataset, TestClickthroughValues, TestConfig}
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class TrainTest extends AnyFlatSpec with Matchers {
  lazy val dataset = RandomDataset.generate(1000)
  lazy val store   = MemPersistence(dataset.mapping.schema)
  lazy val cs      = MemTrainStore()
  lazy val buffer  = ClickthroughJoinBuffer(ClickthroughJoinConfig(), store.values, cs, dataset.mapping)

  it should "generate test data" in {
    Import.slurp(fs2.Stream.emits(dataset.events), store, dataset.mapping, buffer, TestConfig()).unsafeRunSync()
    buffer.flushAll().unsafeRunSync()
  }

  it should "train xgboost model" in {
    val result = train("xgboost")
    result.features.size shouldBe 2
  }

  it should "train xgboost model with a feature subset" in {
    val result = train("xgboost1")
    result.features.size shouldBe 1
  }

// enthropy issue, needs rebuild of native lib
  it should "train lightgbm model" ignore {
    val result = train("lightgbm")
    result.features.size shouldBe 2
  }

  it should "select events per model" in {
    val store = MemTrainStore()
    val ct    = TestClickthroughValues()
    val ct1   = ct.copy(ct = ct.ct.copy(id = EventId("1"), rankingFields = List(StringField("source", "search"))))
    val ct2   = ct.copy(ct = ct.ct.copy(id = EventId("2"), rankingFields = List(StringField("source", "recs"))))
    val model = dataset.mapping.models.values.collectFirst { case m: LambdaMARTPredictor =>
      m.copy(config = m.config.copy(selector = FieldSelector("source", "search")))
    }.get
    store.put(List(ct1, ct2)).unsafeRunSync()
    val load = model.loadDataset(store.getall()).unsafeRunSync()
    load.size shouldBe 1
  }

  def train(name: String) = {
    val model = dataset.mapping.models(name).asInstanceOf[LambdaMARTPredictor]
    Train.train(store, cs, model).unsafeRunSync()
  }
}
