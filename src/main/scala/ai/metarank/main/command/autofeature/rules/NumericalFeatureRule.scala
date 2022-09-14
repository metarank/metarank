package ai.metarank.main.command.autofeature.rules
import ai.metarank.feature.NumberFeature.NumberFeatureSchema
import ai.metarank.main.command.autofeature.EventModel
import ai.metarank.main.command.autofeature.FieldStat.NumericFieldStat
import ai.metarank.model.FieldName.EventType.Item
import ai.metarank.model.{FeatureSchema, FieldName}
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.ScopeType.ItemScopeType
import ai.metarank.util.Logging

object NumericalFeatureRule extends FeatureRule with Logging {
  override def make(model: EventModel): List[FeatureSchema] =
    model.itemFields.nums.flatMap { case (name, stat) => make(name, stat) }.toList

  def make(field: String, stat: NumericFieldStat): Option[NumberFeatureSchema] = {
    val sorted = stat.values.sorted
    (sorted.headOption, sorted.lastOption) match {
      case (Some(min), Some(max)) if min < max =>
        logger.info(s"generated `number` feature for item field $field in the range $min..$max")
        Some(
          NumberFeatureSchema(
            name = FeatureName(field),
            source = FieldName(Item, field),
            scope = ItemScopeType
          )
        )
      case (Some(min), Some(max)) =>
        logger.info(s"field $field is constant with value=$min, skipping")
        None
      case _ => None
    }
  }
}
