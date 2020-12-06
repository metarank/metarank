package me.dfdx.metarank.feature

import me.dfdx.metarank.aggregation.{CountAggregation, ItemMetadataAggregation}
import me.dfdx.metarank.config.FeatureConfig.{CountFeatureConfig, QueryMatchFeatureConfig}
import me.dfdx.metarank.model.Event.RankItem
import me.dfdx.metarank.model.{Featurespace, ItemId, Language, TestItemMetadataEvent, TestRankEvent}
import me.dfdx.metarank.store.HeapStore
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class QueryItemMatchFeatureTest extends AnyFlatSpec with Matchers {
  it should "count matches over existing products" in {
    val store   = new HeapStore(Featurespace("p1"))
    val product = TestItemMetadataEvent("p1", "quick fox jumps over a lazy fox")
    val rank    = TestRankEvent(ItemId("p1"), "fox")
    val agg     = ItemMetadataAggregation(store)
    agg.onEvent(product).unsafeRunSync()
    val itemMatch = QueryItemMatchFeature(agg, QueryMatchFeatureConfig("title"), Language.English)
    val result    = itemMatch.values(rank, RankItem(ItemId("p1"), 1.0f)).unsafeRunSync()
    result shouldBe List(0.2f)
  }
}
