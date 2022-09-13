package ai.metarank.main.autofeature.rule

import ai.metarank.feature.NumberFeature.NumberFeatureSchema
import ai.metarank.main.command.autofeature.FieldStat.NumericFieldStat
import ai.metarank.main.command.autofeature.rules.NumericalFeatureRule
import ai.metarank.model.FieldName
import ai.metarank.model.FieldName.EventType.Item
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.ScopeType.ItemScopeType
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class NumericalFeatureRuleTest extends AnyFlatSpec with Matchers {
  it should "generate on non-const values" in {
    val feature = NumericalFeatureRule.make("price", NumericFieldStat(List(10.0, 20.0, 30.0)))
    feature shouldBe Some(
      NumberFeatureSchema(
        name = FeatureName("price"),
        source = FieldName(Item, "price"),
        scope = ItemScopeType
      )
    )
  }

  it should "skip const fields" in {
    val feature = NumericalFeatureRule.make("price", NumericFieldStat(List(10.0, 10.0, 10.0)))
    feature shouldBe None
  }
}
