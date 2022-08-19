package ai.metarank.rank

import ai.metarank.config.ModelConfig.{LambdaMARTConfig, ModelBackend}
import ai.metarank.config.ModelConfig.ModelBackend.{LightGBMBackend, XGBoostBackend}
import ai.metarank.feature.BaseFeature
import ai.metarank.model.FeatureWeight
import ai.metarank.model.FeatureWeight.{SingularWeight, VectorWeight}
import ai.metarank.rank.Model.{Scorer, TrainedModel}
import io.circe.{Codec, Decoder, DecodingFailure, Encoder, Json, JsonObject}
import io.github.metarank.ltrlib.booster.{Booster, LightGBMBooster, LightGBMOptions, XGBoostBooster, XGBoostOptions}
import io.github.metarank.ltrlib.model.{Dataset, DatasetDescriptor, Feature, Query}
import io.github.metarank.ltrlib.ranking.pairwise.LambdaMART
import org.apache.commons.codec.binary.Hex

case class LambdaMARTModel(
    conf: LambdaMARTConfig,
    features: List[BaseFeature],
    datasetDescriptor: DatasetDescriptor,
    weights: Map[String, Double]
) extends Model {

  override def train(train: Dataset, test: Dataset): TrainedModel = {
    val booster = conf.backend match {
      case LightGBMBackend(it, lr, ndcg, depth, seed, leaves) =>
        val opts = LightGBMOptions(
          trees = it,
          numLeaves = leaves,
          randomSeed = seed,
          learningRate = lr,
          ndcgCutoff = ndcg,
          maxDepth = depth
        )
        LambdaMART(train, opts, LightGBMBooster, Some(test))
      case XGBoostBackend(it, lr, ndcg, depth, seed) =>
        val opts = XGBoostOptions(trees = it, randomSeed = seed, learningRate = lr, ndcgCutoff = ndcg, maxDepth = depth)
        LambdaMART(train, opts, XGBoostBooster, Some(test))
    }
    val result = booster.fit()
    TrainedModel(result.model.save(), weightsMap(train, result.model.weights()), result.iterations)
  }

  private def weightsMap(ds: Dataset, weights: Array[Double]): Map[String, FeatureWeight] = {
    val result = for {
      feature <- ds.desc.features
      offset  <- ds.desc.offsets.get(feature)
    } yield {
      feature match {
        case Feature.SingularFeature(_)     => feature.name -> SingularWeight(weights(offset))
        case Feature.VectorFeature(_, size) => feature.name -> VectorWeight(weights.slice(offset, offset + size))
        case Feature.CategoryFeature(_)     => feature.name -> SingularWeight(weights(offset))
      }
    }
    result.toMap
  }
}

object LambdaMARTModel {
  case class Fillrate(name: String, zeroes: Int, nonzeroes: Int, weight: Double) {
    def print() = s"$name: zero=$zeroes nonzero=$nonzeroes weight=$weight"
  }

  case class LambdaMARTScorer(booster: Booster[_]) extends Scorer {
    override def score(input: Query): Array[Double] = {
      val features = new Array[Double](input.rows * input.columns)
      var pos      = 0
      for {
        rowIndex <- 0 until input.rows
        row = input.getRow(rowIndex)
      } {
        System.arraycopy(row, 0, features, pos, row.length)
        pos += row.length
      }
      booster.predictMat(features, input.rows, input.columns)

    }
  }

  object LambdaMARTScorer {
    def apply(backend: ModelBackend, bytes: Array[Byte]): LambdaMARTScorer = backend match {
      case _: LightGBMBackend => LambdaMARTScorer(LightGBMBooster(bytes))
      case _: XGBoostBackend  => LambdaMARTScorer(XGBoostBooster(bytes))
    }
  }

  implicit val lmEncoder: Encoder[LambdaMARTScorer] = Encoder.instance {
    case s @ LambdaMARTScorer(XGBoostBooster(_)) =>
      Json.fromJsonObject(
        JsonObject.fromMap(
          Map(
            "booster" -> Json.fromString("xgboost"),
            "bytes"   -> Json.fromString(Hex.encodeHexString(s.booster.save()))
          )
        )
      )
    case s @ LambdaMARTScorer(LightGBMBooster(_, _)) =>
      Json.fromJsonObject(
        JsonObject.fromMap(
          Map(
            "booster" -> Json.fromString("lightgbm"),
            "bytes"   -> Json.fromString(Hex.encodeHexString(s.booster.save()))
          )
        )
      )
    case LambdaMARTScorer(_) => ???
  }

  implicit val lmDecoder: Decoder[LambdaMARTScorer] = Decoder.instance(c =>
    for {
      tpe   <- c.downField("booster").as[String]
      bytes <- c.downField("bytes").as[String].map(Hex.decodeHex)
      booster <- tpe match {
        case "xgboost"  => Right(XGBoostBooster(bytes))
        case "lightgbm" => Right(LightGBMBooster(bytes))
        case other      => Left(DecodingFailure(s"booster type $other is not supported", c.history))
      }
    } yield {
      LambdaMARTScorer(booster)
    }
  )

  implicit val lmCodec: Codec[LambdaMARTScorer] = Codec.from(lmDecoder, lmEncoder)
}
