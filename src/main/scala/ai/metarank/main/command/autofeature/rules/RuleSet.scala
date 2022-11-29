package ai.metarank.main.command.autofeature.rules

import ai.metarank.main.CliArgs.AutoFeatureArgs

case class RuleSet(rules: List[FeatureRule])

object RuleSet {
  def stable(args: AutoFeatureArgs): RuleSet = new RuleSet(
    List(
      InteractedWithFeatureRule,
      NumericalFeatureRule,
      StringFeatureRule(percentile = 0.90, countThreshold = args.catThreshold),
      VectorFeatureRule
    )
  )

  def all(args: AutoFeatureArgs): RuleSet = new RuleSet(
    List(
      InteractedWithFeatureRule,
      NumericalFeatureRule,
      StringFeatureRule(minValues = 20, percentile = 0.95, countThreshold = args.catThreshold),
      VectorFeatureRule,
      InteractionRateFeatureRule,
      InteractionCountFeatureRule
    )
  )

  sealed trait RuleSetType {
    def create(args: AutoFeatureArgs): RuleSet
  }
  object RuleSetType {
    case object StableRuleSet extends RuleSetType {
      override def create(args: AutoFeatureArgs): RuleSet = stable(args)
    }
    case object AllRuleSet extends RuleSetType {
      override def create(args: AutoFeatureArgs): RuleSet = all(args)
    }
  }
}
