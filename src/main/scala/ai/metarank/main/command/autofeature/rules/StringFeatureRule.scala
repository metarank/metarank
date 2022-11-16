package ai.metarank.main.command.autofeature.rules
import ai.metarank.feature.StringFeature.EncoderName.{IndexEncoderName, OnehotEncoderName}
import ai.metarank.feature.StringFeature.{OnehotCategoricalEncoder, StringFeatureSchema}
import ai.metarank.main.command.autofeature.EventModel
import ai.metarank.main.command.autofeature.FieldStat.StringFieldStat
import ai.metarank.model.FieldName.EventType.Item
import ai.metarank.model.{FeatureSchema, FieldName}
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.ScopeType.ItemScopeType
import ai.metarank.util.Logging
import cats.data.NonEmptyList

case class StringFeatureRule(
    minValues: Int = 10,
    maxValues: Int = 100,
    percentile: Double = 0.90,
    countThreshold: Double = 0.003
) extends FeatureRule
    with Logging {

  override def make(model: EventModel): List[FeatureSchema] = {
    model.itemFields.strings.flatMap { case (name, stat) => make(name, stat) }.toList
  }

  def fieldValues(stat: StringFieldStat): List[String] = {
    val sorted         = stat.values.filter(_._2 >= 3).toList.sortBy(-_._2)
    val total          = sorted.map(_._2.toLong).sum
    val totalThreshold = percentile * total
    val itemThreshold  = countThreshold * total
    var sum            = 0L
    var count          = 0L
    sorted
      .takeWhile { case (_, c) =>
        sum += c
        count += 1
        (sum <= totalThreshold) || (count <= minValues)
      }
      .filter { case (_, c) => c >= itemThreshold }
      .map(_._1)
      .take(maxValues)
  }

  def make(field: String, stat: StringFieldStat): Option[StringFeatureSchema] = {
    val values = fieldValues(stat)
    values match {
      case head :: tail if (values.size < 10) && (values.size > 1) =>
        logger.info(
          s"item field $field has ${values.size} distinct like-categorial values, generated 'string' feature with onehot encoding for top ${values.size} items"
        )
        Some(
          StringFeatureSchema(
            name = FeatureName(field),
            source = FieldName(Item, field),
            scope = ItemScopeType,
            encode = Some(OnehotEncoderName),
            values = NonEmptyList(head, tail).sorted
          )
        )
      case head :: tail if values.size >= 10 =>
        logger.info(
          s"item field $field has ${stat.values.size} distinct values, generated 'string' feature with index encoding for top ${values.size} items"
        )
        Some(
          StringFeatureSchema(
            name = FeatureName(field),
            source = FieldName(Item, field),
            scope = ItemScopeType,
            encode = Some(IndexEncoderName),
            values = NonEmptyList(head, tail).sorted
          )
        )
      case _ =>
        logger.info(s"field $field is not looking like a categorial value, skipping")
        None
    }
  }
}

object StringFeatureRule {
  case class CategorialHeuristicStat(total: Int, unique: Int, topN: List[(String, Int)])
  object CategorialHeuristicStat {
    def apply(stat: StringFieldStat) = {}
  }
}
