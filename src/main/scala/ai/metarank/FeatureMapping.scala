package ai.metarank

import ai.metarank.config.ModelConfig
import ai.metarank.feature.StringFeature.EncoderName.IndexEncoderName
import ai.metarank.feature.*
import ai.metarank.ml.{Context, Model, Predictor}
import ai.metarank.model.{Dimension, FeatureSchema, Schema}
import ai.metarank.ml.rank.LambdaMARTRanker.{LambdaMARTConfig, LambdaMARTPredictor}
import ai.metarank.ml.rank.NoopRanker.{NoopConfig, NoopPredictor}
import ai.metarank.ml.rank.ShuffleRanker.{ShuffleConfig, ShufflePredictor}
import ai.metarank.ml.recommend.BertSemanticRecommender.{BertSemanticModelConfig, BertSemanticPredictor}
import ai.metarank.ml.recommend.MFRecommender.MFPredictor
import ai.metarank.ml.recommend.TrendingRecommender.{TrendingConfig, TrendingPredictor}
import ai.metarank.ml.recommend.mf.ALSRecImpl
import ai.metarank.ml.recommend.mf.ALSRecImpl.ALSConfig
import ai.metarank.util.Logging
import cats.effect.IO
import io.github.metarank.ltrlib.model.DatasetDescriptor
import io.github.metarank.ltrlib.model.Feature.{CategoryFeature, SingularFeature, VectorFeature}
import cats.implicits.*

case class FeatureMapping(
    features: List[BaseFeature],
    schema: Schema,
    models: Map[String, Predictor[? <: ModelConfig, ? <: Context, ? <: Model[? <: Context]]]
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
  ): IO[FeatureMapping] = {
    for {
      features <- schema.map(s => s.create()).sequence
    } yield {
      val featurySchema = Schema(features.flatMap(_.states))
      val m: List[(String, Predictor[? <: ModelConfig, ? <: Context, ? <: Model[? <: Context]])] = models.toList.map {
        case (name, conf: LambdaMARTConfig) =>
          val modelFeatures = for {
            featureName <- conf.features.toList
            feature     <- features.find(_.schema.name == featureName)
          } yield {
            feature
          }
          name -> LambdaMARTPredictor(name, conf, makeDatasetDescriptor(modelFeatures))

        case (name, conf: NoopConfig)              => name -> NoopPredictor(name, conf)
        case (name, conf: ShuffleConfig)           => name -> ShufflePredictor(name, conf)
        case (name, conf: TrendingConfig)          => name -> TrendingPredictor(name, conf)
        case (name, conf: ALSConfig)               => name -> MFPredictor(name, conf, ALSRecImpl(conf))
        case (name, conf: BertSemanticModelConfig) => name -> BertSemanticPredictor(name, conf)
        case (name, other) => throw new IllegalArgumentException(s"model $name has unsupported config $other")
      }

      new FeatureMapping(
        features = features,
        schema = featurySchema,
        models = m.toMap
      )
    }
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
