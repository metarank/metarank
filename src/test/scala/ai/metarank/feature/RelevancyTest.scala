package ai.metarank.feature

import ai.metarank.feature.RelevancyFeature.RelevancySchema
import ai.metarank.model.Event.ItemRelevancy
import ai.metarank.model.ItemId
import ai.metarank.model.MValue.SingleValue
import ai.metarank.util.TestRankingEvent
import cats.data.NonEmptyList
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class RelevancyTest extends AnyFlatSpec with Matchers {
  it should "extract relevancy" in {
    val feature = RelevancyFeature(RelevancySchema("rel"))
    val event = TestRankingEvent(List("p1", "p2")).copy(items =
      NonEmptyList.of(
        ItemRelevancy(ItemId("p1"), 1),
        ItemRelevancy(ItemId("p2"), 2)
      )
    )
    feature.value(event, Map.empty, ItemRelevancy(ItemId("p1"), 1)) shouldBe SingleValue("rel", 1.0)
  }
}
