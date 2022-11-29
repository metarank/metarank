package ai.metarank.feature

import ai.metarank.feature.BaseFeature.ValueMode
import ai.metarank.feature.RelevancyFeature.RelevancySchema
import ai.metarank.model.Event.RankItem
import ai.metarank.model.Identifier.ItemId
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.MValue.SingleValue
import ai.metarank.util.TestRankingEvent
import cats.data.NonEmptyList
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class RelevancyTest extends AnyFlatSpec with Matchers {
  it should "extract relevancy" in {
    val feature = RelevancyFeature(RelevancySchema(FeatureName("rel")))
    val event = TestRankingEvent(List("p1", "p2")).copy(items =
      NonEmptyList.of(
        RankItem(ItemId("p1"), 1),
        RankItem(ItemId("p2"), 2)
      )
    )
    feature.values(event, Map.empty, ValueMode.OfflineTraining) shouldBe List(
      SingleValue(FeatureName("rel"), 1.0),
      SingleValue(FeatureName("rel"), 2.0)
    )
  }
}
