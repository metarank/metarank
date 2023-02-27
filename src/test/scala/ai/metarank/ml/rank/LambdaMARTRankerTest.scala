package ai.metarank.ml.rank

import ai.metarank.config.BoosterConfig.XGBoostConfig
import ai.metarank.ml.Predictor.EmptyDatasetException
import ai.metarank.ml.PredictorSuite
import ai.metarank.ml.rank.LambdaMARTRanker.{LambdaMARTConfig, LambdaMARTModel, LambdaMARTPredictor}
import ai.metarank.model.ClickthroughValues
import ai.metarank.model.Key.FeatureName
import ai.metarank.util.{TestClickthroughValues, TestQueryRequest}
import cats.data.NonEmptyList
import cats.effect.unsafe.implicits.global
import io.github.metarank.ltrlib.model.DatasetDescriptor
import io.github.metarank.ltrlib.model.Feature.{SingularFeature, VectorFeature}

import scala.util.{Failure, Try}

class LambdaMARTRankerTest extends PredictorSuite[LambdaMARTConfig, QueryRequest, LambdaMARTModel] {
  val conf = LambdaMARTConfig(
    backend = XGBoostConfig(),
    features = NonEmptyList.of(FeatureName("foo")),
    weights = Map("click" -> 1.0)
  )
  val desc = DatasetDescriptor(List(SingularFeature("foo")))

  override def cts = (0 until 100).map(_ => TestClickthroughValues.random(List("p1", "p2", "p3"))).toList

  override def predictor = LambdaMARTPredictor("foo", conf, desc)

  override def request(n: Int): QueryRequest = TestQueryRequest(n)

  it should "fail on ct with no mvalues" in {
    val err = Try(predictor.fit(fs2.Stream(cts.map(_.copy(values = Nil)): _*)).unsafeRunSync())
    err should matchPattern { case Failure(ex: EmptyDatasetException) => // yep
    }
  }

  it should "fail when dataset is too large" in {
    val result = Try(
      LambdaMARTRanker
        .checkDatasetSize(
          itemCount = 3000000,
          dim = 1000,
          groupsCount = 30000,
          List(SingularFeature("foo"), VectorFeature("bar", 999))
        )
        .unsafeRunSync()
    )
    result shouldBe a[Failure[_]]
  }
}
