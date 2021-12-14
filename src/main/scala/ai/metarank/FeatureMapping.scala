package ai.metarank

import ai.metarank.config.Config.InteractionConfig
import ai.metarank.feature.BooleanFeature.BooleanFeatureSchema
import ai.metarank.feature.InteractedWithFeature.InteractedWithSchema
import ai.metarank.feature.MetaFeature.{StatefulFeature, StatelessFeature}
import ai.metarank.feature.NumberFeature.NumberFeatureSchema
import ai.metarank.feature.RateFeature.RateFeatureSchema
import ai.metarank.feature.StringFeature.StringFeatureSchema
import ai.metarank.feature.WordCountFeature.WordCountSchema
import ai.metarank.feature._
import ai.metarank.model.Clickthrough.ItemValues
import ai.metarank.model.Event.{InteractionEvent, RankingEvent}
import ai.metarank.model.{FeatureSchema, FeatureScope, FieldSchema}
import io.findify.featury.model.Key.Tenant
import io.findify.featury.model.{FeatureValue, Key, Schema}
import io.github.metarank.ltrlib.model.DatasetDescriptor
import io.github.metarank.ltrlib.model.Feature.{SingularFeature, VectorFeature}

case class FeatureMapping(
    features: List[MetaFeature],
    statefulFeatures: List[StatefulFeature],
    fields: List[FieldSchema],
    schema: Schema,
    statefulSchema: Schema,
    statelessSchema: Schema,
    weights: Map[String, Double],
    datasetDescriptor: DatasetDescriptor
) {
  def map(
      ranking: RankingEvent,
      source: List[FeatureValue],
      interactions: List[InteractionEvent] = Nil
  ): List[ItemValues] = {
    val state = source.map(fv => fv.key -> fv).toMap
    for {
      item <- ranking.items
    } yield {
      val weight = interactions.find(_.item == item.id).map(e => weights.getOrElse(e.`type`, 1.0)).getOrElse(0.0)
      val values = features.map(_.value(ranking, state, item.id))
      ItemValues(item.id, weight, values)
    }

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
    val stateless = schema.collect {
      case c: NumberFeatureSchema  => NumberFeature(c)
      case c: StringFeatureSchema  => StringFeature(c)
      case c: BooleanFeatureSchema => BooleanFeature(c)
      case c: WordCountSchema      => WordCountFeature(c)
      case c: RateFeatureSchema    => RateFeature(c)
    }
    val stateful = schema.collect { case c: InteractedWithSchema =>
      InteractedWithFeature(c)
    }
    val features: List[MetaFeature] = stateless ++ stateful
    val featurySchema               = Schema(features.flatMap(_.states))
    val datasetFeatures = features.map {
      case f: MetaFeature if f.dim == 1 => SingularFeature(f.schema.name)
      case f: MetaFeature               => VectorFeature(f.schema.name, f.dim)
    }
    val datasetDescriptor = DatasetDescriptor(datasetFeatures)
    new FeatureMapping(
      features = features,
      statefulFeatures = stateful,
      fields = stateless.flatMap(_.fields),
      schema = featurySchema,
      statefulSchema = Schema(stateful.flatMap(_.states)),
      statelessSchema = Schema(stateless.flatMap(_.states)),
      weights = interactions.map(int => int.name -> int.weight).toMap,
      datasetDescriptor = datasetDescriptor
    )
  }

}
