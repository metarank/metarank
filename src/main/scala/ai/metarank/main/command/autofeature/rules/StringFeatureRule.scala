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

case class StringFeatureRule(n: Int = 10, percentile: Double = 0.90) extends FeatureRule with Logging {

  override def make(model: EventModel): List[FeatureSchema] = {
    model.itemFields.strings.flatMap { case (name, stat) => make(name, stat) }.toList
  }

  def fieldValues(stat: StringFieldStat): List[String] = {
    val sorted    = stat.values.toList.sortBy(-_._2)
    val total     = sorted.map(_._2.toLong).sum
    val threshold = percentile * total
    var sum       = 0L
    var count     = 0L
    sorted
      .takeWhile { case (_, c) =>
        sum += c
        count += 1
        (sum <= threshold) || (count <= n)
      }
      .map(_._1)
  }

  def make(field: String, stat: StringFieldStat): Option[StringFeatureSchema] = {
    val values = fieldValues(stat)
    values match {
      case Nil =>
        logger.info(s"item field $field has no known field values, skipping")
        None
      case a :: Nil =>
        logger.info(s"item field $field is constant (value=$a), skipping")
        None
      case a :: b :: Nil =>
        logger.info(
          s"item field $field has two distinct values ($a, $b), generated 'string' feature using label encoding"
        )
        Some(
          StringFeatureSchema(
            name = FeatureName(field),
            source = FieldName(Item, field),
            scope = ItemScopeType,
            encode = IndexEncoderName,
            values = NonEmptyList.of(a, b)
          )
        )
      case head :: tail =>
        logger.info(
          s"item field $field has ${stat.values.size} distinct values, generated 'string' feature with onehot encoding for top ${values.size} items"
        )
        Some(
          StringFeatureSchema(
            name = FeatureName(field),
            source = FieldName(Item, field),
            scope = ItemScopeType,
            encode = OnehotEncoderName,
            values = NonEmptyList(head, tail)
          )
        )
    }
  }
}
