package ai.metarank.feature

import ai.metarank.model.FeatureSchema.{BooleanFeatureSchema, NumberFeatureSchema, StringFeatureSchema}
import ai.metarank.model.{FeatureSchema, FieldSchema}

case class FeatureMapping(features: List[MFeature])

object FeatureMapping {
  def fromFeatureSchema(schema: List[FeatureSchema]) = new FeatureMapping(
    features = schema.map {
      case c: NumberFeatureSchema  => NumberFeature(c)
      case c: StringFeatureSchema  => StringFeature(c)
      case c: BooleanFeatureSchema => BooleanFeature(c)
    }
  )

}
