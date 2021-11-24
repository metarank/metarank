package ai.metarank.feature

import ai.metarank.model.FeatureSchema.{BooleanFeatureSchema, NumberFeatureSchema, StringFeatureSchema, WordCountSchema}
import ai.metarank.model.{FeatureSchema, FieldSchema}

case class FeatureMapping(features: List[MFeature], fields: List[FieldSchema])

object FeatureMapping {
  def fromFeatureSchema(schema: List[FeatureSchema]) = {
    val features = schema.map {
      case c: NumberFeatureSchema  => NumberFeature(c)
      case c: StringFeatureSchema  => StringFeature(c)
      case c: BooleanFeatureSchema => BooleanFeature(c)
      case c: WordCountSchema      => WordCountFeature(c)
    }
    new FeatureMapping(
      features = features,
      fields = features.flatMap(_.fields)
    )
  }

}
