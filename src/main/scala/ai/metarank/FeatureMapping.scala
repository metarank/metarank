package ai.metarank

import ai.metarank.config.ModelConfig
import ai.metarank.config.ModelConfig.{LambdaMARTConfig, NoopConfig, ShuffleConfig}
import ai.metarank.feature.BooleanFeature.BooleanFeatureSchema
import ai.metarank.feature.InteractedWithFeature.InteractedWithSchema
import ai.metarank.feature.FieldMatchFeature.FieldMatchSchema
import ai.metarank.feature.InteractionCountFeature.InteractionCountSchema
import ai.metarank.feature.ItemAgeFeature.ItemAgeSchema
import ai.metarank.feature.LocalDateTimeFeature.LocalDateTimeSchema
import ai.metarank.feature.NumberFeature.NumberFeatureSchema
import ai.metarank.feature.RateFeature.RateFeatureSchema
import ai.metarank.feature.RefererFeature.RefererSchema
import ai.metarank.feature.RelevancyFeature.RelevancySchema
import ai.metarank.feature.StringFeature.EncoderName.IndexEncoderName
import ai.metarank.feature.StringFeature.StringFeatureSchema
import ai.metarank.feature.UserAgentFeature.UserAgentSchema
import ai.metarank.feature.WindowInteractionCountFeature.WindowInteractionCountSchema
import ai.metarank.feature.WordCountFeature.WordCountSchema
import ai.metarank.feature._
import ai.metarank.model.Event.RankingEvent
import ai.metarank.model.{FeatureSchema, FieldName, Key, MValue, Schema, ScopeType}
import ai.metarank.rank.{LambdaMARTModel, Model, NoopModel, ShuffleModel}
import cats.data.{NonEmptyList, NonEmptyMap}
import io.github.metarank.ltrlib.model.DatasetDescriptor
import io.github.metarank.ltrlib.model.Feature.{CategoryFeature, SingularFeature, VectorFeature}

case class FeatureMapping(
    features: List[BaseFeature],
    fields: List[FieldName],
    schema: Schema,
    models: Map[String, Model]
) {
  def stateReadKeys(request: RankingEvent): List[Key] = {
    features.flatMap(_.valueKeys(request).toList)
  }
}

object FeatureMapping {
  def fromFeatureSchema(
      schema: NonEmptyList[FeatureSchema],
      models: NonEmptyMap[String, ModelConfig]
  ) = {
    val features: List[BaseFeature] = schema.collect {
      case c: NumberFeatureSchema    => NumberFeature(c)
      case c: StringFeatureSchema    => StringFeature(c)
      case c: BooleanFeatureSchema   => BooleanFeature(c)
      case c: WordCountSchema        => WordCountFeature(c)
      case c: RateFeatureSchema      => RateFeature(c)
      case c: InteractionCountSchema => InteractionCountFeature(c)
      case c: RelevancySchema        => RelevancyFeature(c)
      case c: UserAgentSchema        => UserAgentFeature(c)
      case c: WindowInteractionCountSchema      => WindowInteractionCountFeature(c)
      case c: LocalDateTimeSchema    => LocalDateTimeFeature(c)
      case c: ItemAgeSchema          => ItemAgeFeature(c)
      case c: FieldMatchSchema       => FieldMatchFeature(c)
      case c: InteractedWithSchema   => InteractedWithFeature(c)
      case c: RefererSchema          => RefererFeature(c)
    }
    val featurySchema = Schema(features.flatMap(_.states))
    val m = models.toNel.toList.map {
      case (name, conf: LambdaMARTConfig) =>
        val modelFeatures = for {
          featureName <- conf.features.toList
          feature     <- features.find(_.schema.name == featureName)
        } yield {
          feature
        }
        name -> LambdaMARTModel(
          conf = conf,
          features = modelFeatures,
          datasetDescriptor = makeDatasetDescriptor(modelFeatures),
          weights = conf.weights
        )

      case (name, conf: NoopConfig)    => name -> NoopModel(conf)
      case (name, conf: ShuffleConfig) => name -> ShuffleModel(conf)
    }

    new FeatureMapping(
      features = features,
      fields = features.flatMap(_.fields),
      schema = featurySchema,
      models = m.toMap
    )
  }

  def makeDatasetDescriptor(features: List[BaseFeature]): DatasetDescriptor = {
    val datasetFeatures = features.map {
      case f: StringFeature if f.schema.encode == IndexEncoderName => CategoryFeature(f.schema.name.value)
      case f: BaseFeature if f.dim == 1                            => SingularFeature(f.schema.name.value)
      case f: BaseFeature                                          => VectorFeature(f.schema.name.value, f.dim)
    }
    DatasetDescriptor(datasetFeatures)
  }

}
