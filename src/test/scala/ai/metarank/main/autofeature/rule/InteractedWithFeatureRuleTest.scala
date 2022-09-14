package ai.metarank.main.autofeature.rule

import ai.metarank.feature.InteractedWithFeature.InteractedWithSchema
import ai.metarank.main.command.autofeature.FieldStat.StringFieldStat
import ai.metarank.main.command.autofeature.rules.{StringFeatureRule, InteractedWithFeatureRule}
import ai.metarank.model.FieldName
import ai.metarank.model.FieldName.EventType.Item
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.ScopeType.UserScopeType
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class InteractedWithFeatureRuleTest extends AnyFlatSpec with Matchers {
  it should "generate interacted_with for cats and ints" in {
    val result = InteractedWithFeatureRule.make("click", "good")
    result shouldBe InteractedWithSchema(
      name = FeatureName("click_good"),
      interaction = "click",
      field = FieldName(Item, "good"),
      scope = UserScopeType,
      count = None,
      duration = None
    )
  }
}
