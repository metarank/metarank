package ai.metarank.main.command.autofeature.rules

case class RuleSet(rules: List[FeatureRule])

object RuleSet {
  def stable(): RuleSet = new RuleSet(
    List(
      InteractedWithFeatureRule,
      NumericalFeatureRule,
      StringFeatureRule(percentile = 0.90),
      RelevancyRule
    )
  )

  def all(): RuleSet = new RuleSet(
    List(
      InteractedWithFeatureRule,
      NumericalFeatureRule,
      StringFeatureRule(minValues = 20, percentile = 0.95),
      RelevancyRule,
      InteractionRateFeatureRule,
      InteractionCountFeatureRule
    )
  )
}
