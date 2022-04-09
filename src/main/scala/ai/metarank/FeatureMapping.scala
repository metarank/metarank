package ai.metarank

import ai.metarank.config.Config.InteractionConfig
import ai.metarank.feature.BooleanFeature.BooleanFeatureSchema
import ai.metarank.feature.InteractedWithFeature.InteractedWithSchema
import ai.metarank.feature.BaseFeature.{ItemFeature, RankingFeature}
import ai.metarank.feature.FieldMatchFeature.FieldMatchSchema
import ai.metarank.feature.InteractionCountFeature.InteractionCountSchema
import ai.metarank.feature.ItemAgeFeature.ItemAgeSchema
import ai.metarank.feature.LocalDateTimeFeature.LocalDateTimeSchema
import ai.metarank.feature.NumberFeature.NumberFeatureSchema
import ai.metarank.feature.RateFeature.RateFeatureSchema
import ai.metarank.feature.RefererFeature.RefererSchema
import ai.metarank.feature.RelevancyFeature.RelevancySchema
import ai.metarank.feature.StringFeature.StringFeatureSchema
import ai.metarank.feature.UserAgentFeature.UserAgentSchema
import ai.metarank.feature.WindowCountFeature.WindowCountSchema
import ai.metarank.feature.WordCountFeature.WordCountSchema
import ai.metarank.feature._
import ai.metarank.model.Clickthrough.ItemValues
import ai.metarank.model.Event.{InteractionEvent, RankingEvent}
import ai.metarank.model.{FeatureSchema, FeatureScope, FieldName, MValue}
import io.findify.featury.model.Key.Tenant
import io.findify.featury.model.{FeatureValue, Key, Schema}
import io.github.metarank.ltrlib.model.DatasetDescriptor
import io.github.metarank.ltrlib.model.Feature.{SingularFeature, VectorFeature}

case class FeatureMapping(
    features: List[BaseFeature],
    fields: List[FieldName],
    schema: Schema,
    weights: Map[String, Double],
    datasetDescriptor: DatasetDescriptor
) {
  def map(
      ranking: RankingEvent,
      source: List[FeatureValue],
      interactions: List[InteractionEvent] = Nil
  ): List[ItemValues] = {
    val state = source.map(fv => fv.key -> fv).toMap

    val itemFeatures: List[ItemFeature] = features.collect { case feature: ItemFeature =>
      feature
    }

    val rankingFeatures = features.collect { case feature: RankingFeature =>
      feature
    }

    val rankingValues = rankingFeatures.map(_.value(ranking, state))

    val itemValuesMatrix = itemFeatures
      .map(feature => {
        val values = feature.values(ranking, state)
        values.foreach { value =>
          if (feature.dim != value.dim)
            throw new IllegalStateException(s"for ${feature.schema} dim mismatch: ${feature.dim} != ${value.dim}")
        }
        values
      })
      .transpose

    val itemScores = for {
      (item, itemValues) <- ranking.items.toList.zip(itemValuesMatrix)
    } yield {
      val weight = interactions.find(_.item == item.id).map(e => weights.getOrElse(e.`type`, 1.0)).getOrElse(0.0)
      ItemValues(item.id, weight, rankingValues ++ itemValues)
    }
    itemScores
  }

  def keys(ranking: RankingEvent): Traversable[Key] = for {
    tag           <- FeatureScope.tags(ranking)
    scopeFeatures <- schema.scopeNameCache.get(tag.scope).toTraversable
    featureName   <- scopeFeatures
  } yield {
    Key(tag, featureName, Tenant(ranking.tenant))
  }
}

object FeatureMapping {
  def fromFeatureSchema(schema: List[FeatureSchema], interactions: List[InteractionConfig]) = {
    val features: List[BaseFeature] = schema.collect {
      case c: NumberFeatureSchema    => NumberFeature(c)
      case c: StringFeatureSchema    => StringFeature(c)
      case c: BooleanFeatureSchema   => BooleanFeature(c)
      case c: WordCountSchema        => WordCountFeature(c)
      case c: RateFeatureSchema      => RateFeature(c)
      case c: InteractionCountSchema => InteractionCountFeature(c)
      case c: RelevancySchema        => RelevancyFeature(c)
      case c: UserAgentSchema        => UserAgentFeature(c)
      case c: WindowCountSchema      => WindowCountFeature(c)
      case c: LocalDateTimeSchema    => LocalDateTimeFeature(c)
      case c: ItemAgeSchema          => ItemAgeFeature(c)
      case c: FieldMatchSchema       => FieldMatchFeature(c)
      case c: InteractedWithSchema   => InteractedWithFeature(c)
      case c: RefererSchema          => RefererFeature(c)
    }
    val featurySchema = Schema(features.flatMap(_.states))
    val datasetFeatures = features.map {
      case f: BaseFeature if f.dim == 1 => SingularFeature(f.schema.name)
      case f: BaseFeature               => VectorFeature(f.schema.name, f.dim)
    }
    val datasetDescriptor = DatasetDescriptor(datasetFeatures)
    new FeatureMapping(
      features = features,
      fields = features.flatMap(_.fields),
      schema = featurySchema,
      weights = interactions.map(int => int.name -> int.weight).toMap,
      datasetDescriptor = datasetDescriptor
    )
  }

}
