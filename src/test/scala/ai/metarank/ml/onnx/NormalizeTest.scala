package ai.metarank.ml.onnx

import ai.metarank.ml.onnx.Normalize.{MinMaxNormalize, PositionNormalize}
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.MValue.SingleValue
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class NormalizeTest extends AnyFlatSpec with Matchers {
  "min-max" should "handle scores" in {
    val result = MinMaxNormalize.scale(
      List(
        SingleValue(FeatureName("foo"), 1.0),
        SingleValue(FeatureName("foo"), 2.0),
        SingleValue(FeatureName("foo"), 3.0)
      )
    )
    result.map(_.value) shouldBe List(0.0, 0.5, 1.0)
  }

  it should "handle nans" in {
    val result = MinMaxNormalize.scale(
      List(
        SingleValue(FeatureName("foo"), 1.0),
        SingleValue(FeatureName("foo"), 2.0),
        SingleValue(FeatureName("foo"), Double.NaN)
      )
    )
    result.map(_.value).take(2) shouldBe List(0.0, 1.0)
    result.lastOption.map(_.value.isNaN) shouldBe Some(true)
  }

  "pos norm" should "handle lists" in {
    val result = PositionNormalize.scale(
      List(
        SingleValue(FeatureName("foo"), 1.0),
        SingleValue(FeatureName("foo"), 4.0),
        SingleValue(FeatureName("foo"), 3.0),
        SingleValue(FeatureName("foo"), 2.0),
        SingleValue(FeatureName("foo"), 5.0)
      )
    )
    result.map(_.value) shouldBe List(0.0, 0.6, 0.4, 0.2, 0.8)
  }

  it should "handle nans" in {
    val result = PositionNormalize.scale(
      List(
        SingleValue(FeatureName("foo"), Double.NaN),
        SingleValue(FeatureName("foo"), 1.0),
        SingleValue(FeatureName("foo"), 4.0),
        SingleValue(FeatureName("foo"), 3.0),
        SingleValue(FeatureName("foo"), 2.0)
      )
    )
    result.map(_.value).drop(1) shouldBe List(0.0, 0.6, 0.4, 0.2)
    result.headOption.map(_.value.isNaN) shouldBe Some(true)

  }
}
