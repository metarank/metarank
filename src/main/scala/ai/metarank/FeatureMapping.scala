package ai.metarank

import ai.metarank.config.ModelConfig
import ai.metarank.feature.BooleanFeature.BooleanFeatureSchema
import ai.metarank.feature.DiversityFeature.DiversitySchema
import ai.metarank.feature.InteractedWithFeature.InteractedWithSchema
import ai.metarank.feature.FieldMatchFeature.FieldMatchSchema
import ai.metarank.feature.InteractionCountFeature.InteractionCountSchema
import ai.metarank.feature.ItemAgeFeature.ItemAgeSchema
import ai.metarank.feature.LocalDateTimeFeature.LocalDateTimeSchema
import ai.metarank.feature.NumVectorFeature.VectorFeatureSchema
import ai.metarank.feature.NumberFeature.NumberFeatureSchema
import ai.metarank.feature.PositionFeature.PositionFeatureSchema
import ai.metarank.feature.RandomFeature.RandomFeatureSchema
import ai.metarank.feature.RateFeature.RateFeatureSchema
import ai.metarank.feature.RefererFeature.RefererSchema
import ai.metarank.feature.RelevancyFeature.RelevancySchema
import ai.metarank.feature.StringFeature.EncoderName.IndexEncoderName
import ai.metarank.feature.StringFeature.StringFeatureSchema
import ai.metarank.feature.UserAgentFeature.UserAgentSchema
import ai.metarank.feature.WindowInteractionCountFeature.WindowInteractionCountSchema
import ai.metarank.feature.WordCountFeature.WordCountSchema
import ai.metarank.feature._
import ai.metarank.ml.{Context, Model, Predictor}
import ai.metarank.model.{Dimension, FeatureSchema, FieldName, Key, MValue, Schema, ScopeType}
import ai.metarank.ml.rank.LambdaMARTRanker.{LambdaMARTConfig, LambdaMARTModel, LambdaMARTPredictor}
import ai.metarank.ml.rank.NoopRanker.{NoopConfig, NoopModel, NoopPredictor}
import ai.metarank.ml.rank.ShuffleRanker.{ShuffleConfig, ShuffleModel, ShufflePredictor}
import ai.metarank.ml.recommend.MFRecommender.MFPredictor
import ai.metarank.ml.recommend.TrendingRecommender.{TrendingConfig, TrendingPredictor}
import ai.metarank.ml.recommend.mf.ALSRecImpl
import ai.metarank.ml.recommend.mf.ALSRecImpl.ALSConfig
import ai.metarank.util.Logging
import cats.data.{NonEmptyList, NonEmptyMap}
import io.github.metarank.ltrlib.model.DatasetDescriptor
import io.github.metarank.ltrlib.model.Feature.{CategoryFeature, SingularFeature, VectorFeature}

case class FeatureMapping(
    features: List[BaseFeature],
    schema: Schema,
    models: Map[String, Predictor[_ <: ModelConfig, _ <: Context, _ <: Model[_ <: Context]]]
) extends Logging {
  def hasRankingModel = {
    models.values.exists {
      case predictor: LambdaMARTPredictor => true
      case _                              => false
    }
  }
}

object FeatureMapping extends Logging {

  def fromFeatureSchema(
      schema: List[FeatureSchema],
      models: Map[String, ModelConfig]
  ) = {
    val features: List[BaseFeature] = schema
      .collect {
        case c: NumberFeatureSchema          => NumberFeature(c)
        case c: StringFeatureSchema          => StringFeature(c)
        case c: BooleanFeatureSchema         => BooleanFeature(c)
        case c: WordCountSchema              => WordCountFeature(c)
        case c: RateFeatureSchema            => RateFeature(c)
        case c: InteractionCountSchema       => InteractionCountFeature(c)
        case c: RelevancySchema              => RelevancyFeature(c)
        case c: UserAgentSchema              => UserAgentFeature(c)
        case c: WindowInteractionCountSchema => WindowInteractionCountFeature(c)
        case c: LocalDateTimeSchema          => LocalDateTimeFeature(c)
        case c: ItemAgeSchema                => ItemAgeFeature(c)
        case c: FieldMatchSchema             => FieldMatchFeature(c)
        case c: InteractedWithSchema         => InteractedWithFeature(c)
        case c: RefererSchema                => RefererFeature(c)
        case c: PositionFeatureSchema        => PositionFeature(c)
        case c: VectorFeatureSchema          => NumVectorFeature(c)
        case c: RandomFeatureSchema          => RandomFeature(c)
        case c: DiversitySchema              => DiversityFeature(c)
      }

    val featurySchema = Schema(features.flatMap(_.states))
    val m: List[(String, Predictor[_ <: ModelConfig, _ <: Context, _ <: Model[_ <: Context]])] = models.toList.map {
      case (name, conf: LambdaMARTConfig) =>
        val modelFeatures = for {
          featureName <- conf.features.toList
          feature     <- features.find(_.schema.name == featureName)
        } yield {
          feature
        }
        name -> LambdaMARTPredictor(name, conf, makeDatasetDescriptor(modelFeatures))

      case (name, conf: NoopConfig)     => name -> NoopPredictor(name, conf)
      case (name, conf: ShuffleConfig)  => name -> ShufflePredictor(name, conf)
      case (name, conf: TrendingConfig) => name -> TrendingPredictor(name, conf)
      case (name, conf: ALSConfig)      => name -> MFPredictor(name, conf, ALSRecImpl(conf))
    }

    new FeatureMapping(
      features = features,
      schema = featurySchema,
      models = m.toMap
    )
  }

  def makeDatasetDescriptor(features: List[BaseFeature]): DatasetDescriptor = {
    val datasetFeatures = features.map {
      case f: StringFeature if f.schema.encode.contains(IndexEncoderName) => CategoryFeature(f.schema.name.value)
      case f: BaseFeature =>
        f.dim match {
          case Dimension.VectorDim(dim) => VectorFeature(f.schema.name.value, dim)
          case Dimension.SingleDim      => SingularFeature(f.schema.name.value)
        }
    }
    DatasetDescriptor(datasetFeatures)
  }

}
