package ai.metarank.main.command.autofeature.rules

case class RuleSet(rules: List[FeatureRule])

object RuleSet {
  def stable(): RuleSet = new RuleSet(
    List(
      InteractionFeatureRule,
      NumericalFeatureRule,
      StringFeatureRule()
    )
  )

  def all(): RuleSet = new RuleSet(
    List(
      InteractionFeatureRule,
      NumericalFeatureRule,
      StringFeatureRule(n = 20, percentile = 1.0),
      InteractionRateFeatureRule,
      InteractionCountFeatureRule
    )
  )
}
