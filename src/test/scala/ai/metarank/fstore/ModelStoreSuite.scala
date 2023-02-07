package ai.metarank.fstore

import ai.metarank.config.BoosterConfig.XGBoostConfig
import ai.metarank.fstore.Persistence.{ModelName, ModelStore}
import ai.metarank.ml.rank.LambdaMARTRanker.{LambdaMARTConfig, LambdaMARTModel, LambdaMARTPredictor}
import ai.metarank.ml.rank.ShuffleRanker.{ShuffleConfig, ShuffleModel, ShufflePredictor}
import ai.metarank.model.Key.FeatureName
import better.files.Resource
import cats.data.NonEmptyList
import cats.effect.unsafe.implicits.global
import io.github.metarank.ltrlib.booster.{LightGBMBooster, XGBoostBooster}
import io.github.metarank.ltrlib.model.DatasetDescriptor
import io.github.metarank.ltrlib.model.Feature.SingularFeature
import org.apache.commons.io.IOUtils
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.util.Try

trait ModelStoreSuite extends AnyFlatSpec with Matchers {
  def store: ModelStore

  val model = LambdaMARTModel(
    "lgbm",
    LambdaMARTConfig(
      backend = XGBoostConfig(),
      features = NonEmptyList.one(FeatureName("foo")),
      weights = Map("click" -> 1)
    ),
    booster = LightGBMBooster.apply(IOUtils.toByteArray(Resource.my.getAsStream("/ranklens/ranklens.model")))
  )

  val predictor = LambdaMARTPredictor("lgbm", model.conf, DatasetDescriptor(List(SingularFeature("foo"))))

  it should "put-get" in {
    store.put(model).unsafeRunSync()
    val read = store.get(ModelName("lgbm"), predictor).unsafeRunSync()
    read.map(_.conf) shouldBe Some(model.conf)
  }
}
