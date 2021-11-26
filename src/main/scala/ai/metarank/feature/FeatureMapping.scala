package ai.metarank.feature

import ai.metarank.config.Config.InteractionConfig
import ai.metarank.feature.BooleanFeature.BooleanFeatureSchema
import ai.metarank.feature.InteractedWithFeature.InteractedWithSchema
import ai.metarank.feature.NumberFeature.NumberFeatureSchema
import ai.metarank.feature.RateFeature.RateFeatureSchema
import ai.metarank.feature.StringFeature.StringFeatureSchema
import ai.metarank.feature.WordCountFeature.WordCountSchema
import ai.metarank.model.Clickthrough.ItemValues
import ai.metarank.model.Event.{InteractionEvent, RankingEvent}
import ai.metarank.model.{Clickthrough, FeatureSchema, FieldSchema}
import io.findify.featury.model.{FeatureValue, Schema}
import io.github.metarank.ltrlib.model.DatasetDescriptor
import io.github.metarank.ltrlib.model.Feature.{SingularFeature, VectorFeature}

case class FeatureMapping(
    features: List[MFeature],
    fields: List[FieldSchema],
    underlyingSchema: Schema,
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
}

object FeatureMapping {
  def fromFeatureSchema(schema: List[FeatureSchema], interactions: List[InteractionConfig]) = {
    val features = schema.map {
      case c: NumberFeatureSchema  => NumberFeature(c)
      case c: StringFeatureSchema  => StringFeature(c)
      case c: BooleanFeatureSchema => BooleanFeature(c)
      case c: WordCountSchema      => WordCountFeature(c)
      case c: RateFeatureSchema    => RateFeature(c)
      case c: InteractedWithSchema => InteractedWithFeature(c)
    }
    val featurySchema = Schema(features.flatMap(_.states))
    val dataset = features.map {
      case f: MFeature if f.dim == 1 => SingularFeature(f.schema.name)
      case f: MFeature               => VectorFeature(f.schema.name, f.dim)
    }
    new FeatureMapping(
      features = features,
      fields = features.flatMap(_.fields),
      underlyingSchema = featurySchema,
      weights = interactions.map(int => int.name -> int.weight).toMap,
      datasetDescriptor = DatasetDescriptor(dataset)
    )
  }

}
