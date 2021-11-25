package ai.metarank.feature

import ai.metarank.feature.BooleanFeature.BooleanFeatureSchema
import ai.metarank.feature.InteractedWithFeature.InteractedWithSchema
import ai.metarank.feature.NumberFeature.NumberFeatureSchema
import ai.metarank.feature.RateFeature.RateFeatureSchema
import ai.metarank.feature.StringFeature.StringFeatureSchema
import ai.metarank.feature.WordCountFeature.WordCountSchema
import ai.metarank.model.{FeatureSchema, FieldSchema}

case class FeatureMapping(features: List[MFeature], fields: List[FieldSchema])

object FeatureMapping {
  def fromFeatureSchema(schema: List[FeatureSchema]) = {
    val features = schema.map {
      case c: NumberFeatureSchema  => NumberFeature(c)
      case c: StringFeatureSchema  => StringFeature(c)
      case c: BooleanFeatureSchema => BooleanFeature(c)
      case c: WordCountSchema      => WordCountFeature(c)
      case c: RateFeatureSchema    => RateFeature(c)
      case c: InteractedWithSchema => InteractedWithFeature(c)
    }
    new FeatureMapping(
      features = features,
      fields = features.flatMap(_.fields)
    )
  }

}
