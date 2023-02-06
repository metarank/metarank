package ai.metarank.main.autofeature.model

import ai.metarank.feature.NumberFeature.NumberFeatureSchema
import ai.metarank.main.command.autofeature.model.TrendingRecsConfigGenerator
import ai.metarank.main.command.autofeature.{EventCountStat, EventModel, InteractionStat}
import ai.metarank.model.FieldName
import ai.metarank.model.FieldName.EventType.Item
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.ScopeType.ItemScopeType
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class TrendingRecsConfigGeneratorTest extends AnyFlatSpec with Matchers {
  it should "generate trending recs when enough clicks" in {
    val em = EventModel(eventCount = EventCountStat(ints = 100), interactions = InteractionStat(Map("click" -> 100)))
    val features = List(NumberFeatureSchema(FeatureName("foo"), FieldName(Item, "foo"), ItemScopeType))
    val result   = TrendingRecsConfigGenerator.maybeGenerate(em, features)
    result.isDefined shouldBe true
  }

  it should "fail if there's not enough clicks" in {
    val em       = EventModel(eventCount = EventCountStat(ints = 1), interactions = InteractionStat(Map("click" -> 1)))
    val features = List(NumberFeatureSchema(FeatureName("foo"), FieldName(Item, "foo"), ItemScopeType))
    val result   = TrendingRecsConfigGenerator.maybeGenerate(em, features)
    result.isDefined shouldBe false
  }
}
