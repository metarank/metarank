package ai.metarank

import ai.metarank.config.ModelConfig
import ai.metarank.config.ModelConfig.{LambdaMARTConfig, NoopConfig, ShuffleConfig}
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
import ai.metarank.rank.{LambdaMARTModel, Model, NoopModel, ShuffleModel}
import cats.data.{NonEmptyList, NonEmptyMap}
import io.findify.featury.model.Key.Tenant
import io.findify.featury.model.{FeatureValue, Key, Schema}
import io.github.metarank.ltrlib.model.DatasetDescriptor
import io.github.metarank.ltrlib.model.Feature.{SingularFeature, VectorFeature}

case class FeatureMapping(
    features: List[BaseFeature],
    fields: List[FieldName],
    schema: Schema,
    models: Map[String, Model]
) {

  def keys(ranking: RankingEvent): Iterable[Key] = for {
    tag           <- FeatureScope.tags(ranking)
    scopeFeatures <- schema.scopeNameCache.get(tag.scope).toSeq
    featureName   <- scopeFeatures
  } yield {
    Key(tag, featureName, Tenant(ranking.tenant))
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
      case c: WindowCountSchema      => WindowCountFeature(c)
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
      case f: BaseFeature if f.dim == 1 => SingularFeature(f.schema.name)
      case f: BaseFeature               => VectorFeature(f.schema.name, f.dim)
    }
    DatasetDescriptor(datasetFeatures)
  }

}
