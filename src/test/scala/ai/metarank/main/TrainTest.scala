package ai.metarank.main

import ai.metarank.fstore.memory.MemPersistence
import ai.metarank.main.command.{Import, Train}
import ai.metarank.rank.LambdaMARTModel
import ai.metarank.util.RandomDataset
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class TrainTest extends AnyFlatSpec with Matchers {
  lazy val dataset = RandomDataset.generate(1000)
  lazy val store   = MemPersistence(dataset.mapping.schema)

  it should "generate test data" in {
    Import.slurp(fs2.Stream.emits(dataset.events), store, dataset.mapping).unsafeRunSync()
  }

  it should "train xgboost model" in {
    val result = train("xgboost")
    result.iterations.size shouldBe 10
    result.features.size shouldBe 2
  }

  it should "train lightgbm model" in {
    val result = train("lightgbm")
    result.iterations.size shouldBe 10
    result.features.size shouldBe 2
  }

  def train(name: String) = {
    val model = dataset.mapping.models(name).asInstanceOf[LambdaMARTModel]
    Train.train(store, model, "xgboost", model.conf.backend).unsafeRunSync()
  }
}