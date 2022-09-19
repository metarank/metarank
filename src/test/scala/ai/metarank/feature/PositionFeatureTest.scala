package ai.metarank.feature

import ai.metarank.feature.BaseFeature.ValueMode
import ai.metarank.feature.PositionFeature.PositionFeatureSchema
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.MValue.SingleValue
import ai.metarank.util.TestRankingEvent
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class PositionFeatureTest extends AnyFlatSpec with Matchers {
  val feature = PositionFeature(PositionFeatureSchema(FeatureName("pos"), 5))

  it should "be equal to position on train" in {
    val result = feature.values(TestRankingEvent(List("p1", "p2", "p3")), Map.empty, ValueMode.OfflineTraining)
    result shouldBe List(
      SingleValue(feature.schema.name, 0),
      SingleValue(feature.schema.name, 1),
      SingleValue(feature.schema.name, 2)
    )
  }

  it should "be constant online" in {
    val result = feature.values(TestRankingEvent(List("p1", "p2", "p3")), Map.empty, ValueMode.OnlineInference)
    result shouldBe List(
      SingleValue(feature.schema.name, 5),
      SingleValue(feature.schema.name, 5),
      SingleValue(feature.schema.name, 5)
    )
  }
}
