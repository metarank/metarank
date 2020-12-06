package me.dfdx.metarank.feature

import cats.effect.IO
import me.dfdx.metarank.aggregation.ItemMetadataAggregation
import me.dfdx.metarank.aggregation.Scope.{GlobalScope, RankType}
import me.dfdx.metarank.config.FeatureConfig.QueryMatchFeatureConfig
import me.dfdx.metarank.model.{Event, Language}
import me.dfdx.metarank.model.Field.StringField
import shapeless.syntax.typeable._

case class QueryItemMatchFeature(
    itemAggregation: ItemMetadataAggregation,
    conf: QueryMatchFeatureConfig,
    language: Language
) extends Feature {
  val items = itemAggregation.store.kv(itemAggregation.feed, GlobalScope(RankType))
  override def values(event: Event.RankEvent, item: Event.RankItem): IO[List[Float]] = {
    val br = 1
    for {
      itemMetadataOption <- items.get(item.id)
    } yield {
      val br = 1
      val result = for {
        itemMetadata <- itemMetadataOption
        field        <- itemMetadata.fields.find(_.name == conf.field)
        fieldString  <- field.cast[StringField]
        query        <- event.context.query
      } yield {
        val queryTokens  = language.tokenize(query)
        val fieldTokens  = language.tokenize(fieldString.value)
        val union        = (queryTokens ++ fieldTokens).distinct.size
        val intersection = queryTokens.intersect(fieldTokens).size
        if (intersection != 0) {
          List(intersection.toFloat / union.toFloat)
        } else {
          List(0.0f)
        }
      }
      result.getOrElse(List[Float](0.0f))

    }
  }
}
