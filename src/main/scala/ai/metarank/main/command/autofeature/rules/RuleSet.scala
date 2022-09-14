package ai.metarank.main.command.autofeature.rules

case class RuleSet(rules: List[FeatureRule])

object RuleSet {
  def stable(): RuleSet = new RuleSet(
    List(
      InteractionFeatureRule,
      NumericalFeatureRule,
      StringFeatureRule(percentile = 0.80),
      RelevancyRule
    )
  )

  def all(): RuleSet = new RuleSet(
    List(
      InteractionFeatureRule,
      NumericalFeatureRule,
      StringFeatureRule(min = 20, percentile = 0.95),
      RelevancyRule,
      InteractionRateFeatureRule,
      InteractionCountFeatureRule
    )
  )
}
