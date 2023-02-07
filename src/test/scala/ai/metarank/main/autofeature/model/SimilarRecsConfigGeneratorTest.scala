package ai.metarank.main.autofeature.model

import ai.metarank.feature.NumberFeature.NumberFeatureSchema
import ai.metarank.main.command.autofeature.model.ModelGenerator.ModelConfigMirror
import ai.metarank.main.command.autofeature.{EventCountStat, EventModel, InteractionStat}
import ai.metarank.main.command.autofeature.model.{SimilarRecsConfigGenerator, TrendingRecsConfigGenerator}
import ai.metarank.ml.recommend.mf.ALSRecImpl.ALSConfig
import ai.metarank.model.FieldName
import ai.metarank.model.FieldName.EventType.Item
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.ScopeType.ItemScopeType
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SimilarRecsConfigGeneratorTest extends AnyFlatSpec with Matchers {
  it should "generate similar recs when enough clicks" in {
    val em = EventModel(eventCount = EventCountStat(ints = 100), interactions = InteractionStat(Map("click" -> 100)))
    val features = List(NumberFeatureSchema(FeatureName("foo"), FieldName(Item, "foo"), ItemScopeType))
    val result   = SimilarRecsConfigGenerator.maybeGenerate(em, features)
    result shouldBe Some(ModelConfigMirror("similar", ALSConfig(List("click"))))
  }

  it should "fail if there's not enough clicks" in {
    val em       = EventModel(eventCount = EventCountStat(ints = 1), interactions = InteractionStat(Map("click" -> 1)))
    val features = List(NumberFeatureSchema(FeatureName("foo"), FieldName(Item, "foo"), ItemScopeType))
    val result   = SimilarRecsConfigGenerator.maybeGenerate(em, features)
    result shouldBe None
  }
}
