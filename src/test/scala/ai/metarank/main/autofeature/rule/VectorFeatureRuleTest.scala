package ai.metarank.main.autofeature.rule

import ai.metarank.feature.NumVectorFeature.Reducer.VectorReducer
import ai.metarank.feature.NumVectorFeature.VectorFeatureSchema
import ai.metarank.main.command.autofeature.FieldStat.NumericListFieldStat
import ai.metarank.main.command.autofeature.rules.VectorFeatureRule
import ai.metarank.model.FieldName
import ai.metarank.model.FieldName.EventType.Item
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.ScopeType.ItemScopeType
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper

class VectorFeatureRuleTest extends AnyFlatSpec with Matchers {
  it should "generate embedding configs" in {
    val result = VectorFeatureRule.make("vec", NumericListFieldStat(List(1.0, 2.0, 3.0), Map(10 -> 1)))
    result shouldBe Some(
      VectorFeatureSchema(FeatureName("vec"), FieldName(Item, "vec"), Some(List(VectorReducer(10))), ItemScopeType)
    )
  }

  it should "generate ver-len vector configs" in {
    val result = VectorFeatureRule.make("vec", NumericListFieldStat(List(1.0, 2.0, 3.0), Map(1 -> 1, 2 -> 2)))
    result shouldBe Some(
      VectorFeatureSchema(FeatureName("vec"), FieldName(Item, "vec"), None, ItemScopeType)
    )

  }
}
