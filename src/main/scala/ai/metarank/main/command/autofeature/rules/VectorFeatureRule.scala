package ai.metarank.main.command.autofeature.rules

import ai.metarank.feature.NumVectorFeature.Reducer._
import ai.metarank.feature.NumVectorFeature.VectorFeatureSchema
import ai.metarank.main.command.autofeature.EventModel
import ai.metarank.main.command.autofeature.FieldStat.NumericListFieldStat
import ai.metarank.model.FieldName.EventType.Item
import ai.metarank.model.{FeatureSchema, FieldName}
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.ScopeType.ItemScopeType
import ai.metarank.util.Logging

object VectorFeatureRule extends FeatureRule with Logging {
  override def make(model: EventModel): List[FeatureSchema] = {
    val fields = model.itemFields.numlists ++ model.rankFields.numlists
    fields.flatMap { case (name, stat) => make(name, stat) }.toList
  }

  def make(field: String, stat: NumericListFieldStat): Option[VectorFeatureSchema] = {
    val sorted = stat.values.sorted
    (sorted.headOption, sorted.lastOption) match {
      case (Some(min), Some(max)) if min < max =>
        stat.sizes.keys.toList match {
          case size :: Nil =>
            logger.info(s"generated `vector` feature for constant $size-sized item field $field")
            Some(
              VectorFeatureSchema(
                name = FeatureName(field),
                source = FieldName(Item, field),
                scope = ItemScopeType,
                reduce = Some(List(VectorReducer(size)))
              )
            )
          case other =>
            logger.info(s"generated `vector` feature for variable sized item field $field (sizes: $other)")
            Some(
              VectorFeatureSchema(
                name = FeatureName(field),
                source = FieldName(Item, field),
                scope = ItemScopeType
              )
            )
        }

      case (Some(min), Some(max)) =>
        logger.info(s"field $field is constant with value=$min, skipping")
        None
      case _ => None
    }

  }
}
