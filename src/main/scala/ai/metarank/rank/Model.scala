package ai.metarank.rank

import ai.metarank.config.ModelConfig
import ai.metarank.feature.BaseFeature
import ai.metarank.model.{FeatureValue, FeatureWeight, ItemValue, Schema, TrainResult}
import ai.metarank.model.Event.{InteractionEvent, RankingEvent}
import ai.metarank.rank.LambdaMARTModel.LambdaMARTScorer
import ai.metarank.rank.Model.TrainedModel
import ai.metarank.rank.NoopModel.NoopScorer
import ai.metarank.rank.ShuffleModel.ShuffleScorer
import ai.metarank.util.Logging
import io.circe.{Codec, Decoder, DecodingFailure, Encoder, Json, JsonObject}
import io.github.metarank.ltrlib.model.FitResult.IterationResult
import io.github.metarank.ltrlib.model.{Dataset, DatasetDescriptor, Query}

trait Model extends Logging {
  def conf: ModelConfig
  def features: List[BaseFeature]
  def datasetDescriptor: DatasetDescriptor
  def train(train: Dataset, test: Dataset): TrainedModel
}

object Model {
  case class TrainedModel(
      bytes: Array[Byte],
      weights: Map[String, FeatureWeight] = Map.empty,
      iterations: List[IterationResult] = Nil
  )
  trait Scorer {
    def score(input: Query): Array[Double]
  }

  implicit val scorerDecoder: Decoder[Scorer] = Decoder.instance(c =>
    for {
      tpe <- c.downField("type").as[String]
      decoded <- tpe match {
        case "noop"       => Right(NoopScorer)
        case "lambdamart" => LambdaMARTModel.lmDecoder(c)
        case "shuffle"    => ShuffleModel.shuffleScorerCodec(c)
        case other        => Left(DecodingFailure(s"scorer type $other not supported", c.history))
      }
    } yield {
      decoded
    }
  )

  implicit val scorerEncoder: Encoder[Scorer] = Encoder.instance {
    case s: LambdaMARTScorer => LambdaMARTModel.lmEncoder(s).deepMerge(tpe("lambdamart"))
    case NoopScorer          => tpe("noop")
    case s: ShuffleScorer    => ShuffleModel.shuffleScorerCodec(s)
  }

  implicit val scorerCodec: Codec[Scorer] = Codec.from(scorerDecoder, scorerEncoder)

  def tpe(name: String) = Json.fromJsonObject(JsonObject.fromMap(Map("type" -> Json.fromString(name))))
}
