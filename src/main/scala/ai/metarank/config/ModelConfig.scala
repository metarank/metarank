package ai.metarank.config

import ai.metarank.config.Selector.AcceptSelector
import ai.metarank.model.Key.FeatureName
import cats.data.{NonEmptyList, NonEmptyMap}
import io.circe.{Codec, Decoder, DecodingFailure, Encoder, Json, JsonObject}
import io.circe.generic.semiauto._

import scala.concurrent.duration._
import scala.util.Random

sealed trait ModelConfig {
  def selector: Selector
}

object ModelConfig {
  import ai.metarank.util.DurationJson._

  case class LambdaMARTConfig(
      backend: ModelBackend,
      features: NonEmptyList[FeatureName],
      weights: Map[String, Double],
      selector: Selector = AcceptSelector()
  ) extends ModelConfig
  case class ShuffleConfig(maxPositionChange: Int, selector: Selector = AcceptSelector()) extends ModelConfig
  case class NoopConfig(selector: Selector = AcceptSelector())                            extends ModelConfig

  implicit val noopDecoder: Decoder[NoopConfig] =
    Decoder.instance(c =>
      c.downField("selector").as[Option[Selector]].map(_.getOrElse(AcceptSelector())).map(NoopConfig)
    )
  implicit val noopEncoder: Encoder[NoopConfig] = deriveEncoder[NoopConfig]

  implicit val shuffleDecoder: Decoder[ShuffleConfig] = Decoder.instance(c =>
    for {
      mpc      <- c.downField("maxPositionChange").as[Int]
      selector <- c.downField("selector").as[Option[Selector]].map(_.getOrElse(AcceptSelector()))
    } yield {
      ShuffleConfig(mpc, selector)
    }
  )
  implicit val shuffleEncoder: Encoder[ShuffleConfig] = deriveEncoder[ShuffleConfig]

  implicit val lmDecoder: Decoder[LambdaMARTConfig] = Decoder.instance(c =>
    for {
      backend  <- c.downField("backend").as[ModelBackend]
      features <- c.downField("features").as[NonEmptyList[FeatureName]]
      weights  <- c.downField("weights").as[Map[String, Double]]
      selector <- c.downField("selector").as[Option[Selector]].map(_.getOrElse(AcceptSelector()))
    } yield {
      LambdaMARTConfig(backend, features, weights, selector)
    }
  )
  implicit val lmEncoder: Encoder[LambdaMARTConfig] = deriveEncoder

  implicit val modelConfigEncoder: Encoder[ModelConfig] = Encoder.instance {
    case lm: LambdaMARTConfig => lmEncoder(lm).deepMerge(withType("lambdamart"))
    case s: ShuffleConfig     => shuffleEncoder(s).deepMerge(withType("shuffle"))
    case n: NoopConfig        => noopEncoder(n).deepMerge(withType("noop"))
  }

  implicit val modelConfigDecoder: Decoder[ModelConfig] = Decoder.instance(c =>
    c.downField("type").as[String] match {
      case Right("lambdamart") => lmDecoder.tryDecode(c)
      case Right("shuffle")    => shuffleDecoder.tryDecode(c)
      case Right("noop")       => noopDecoder.tryDecode(c)
      case Right(other)        => Left(DecodingFailure(s"cannot decode model $other", c.history))
      case Left(err)           => Left(err)
    }
  )

  def withType(tpe: String): Json = Json.fromJsonObject(JsonObject.fromIterable(List("type" -> Json.fromString(tpe))))

  sealed trait ModelBackend {
    def iterations: Int
    def learningRate: Double
    def ndcgCutoff: Int
    def maxDepth: Int
    def seed: Int
    def sampling: Double
  }
  object ModelBackend {
    case class LightGBMBackend(
        iterations: Int = 100,
        learningRate: Double = 0.1,
        ndcgCutoff: Int = 10,
        maxDepth: Int = 8,
        seed: Int = Random.nextInt(Int.MaxValue),
        numLeaves: Int = 16,
        sampling: Double = 0.8
    ) extends ModelBackend
    case class XGBoostBackend(
        iterations: Int = 100,
        learningRate: Double = 0.1,
        ndcgCutoff: Int = 10,
        maxDepth: Int = 8,
        seed: Int = Random.nextInt(Int.MaxValue),
        sampling: Double = 0.8
    ) extends ModelBackend

    implicit val lgbmDecoder: Decoder[LightGBMBackend] = Decoder.instance(c =>
      for {
        iterationsOption   <- c.downField("iterations").as[Option[Int]]
        learningRateOption <- c.downField("learningRate").as[Option[Double]]
        ndcgCutoffOption   <- c.downField("ndcgCutoff").as[Option[Int]]
        maxDepthOption     <- c.downField("maxDepth").as[Option[Int]]
        seedOption         <- c.downField("seed").as[Option[Int]]
        numLeavesOption    <- c.downField("numLeaves").as[Option[Int]]
        samplingOption     <- c.downField("sampling").as[Option[Double]]
      } yield {
        val empty = LightGBMBackend()
        LightGBMBackend(
          iterations = iterationsOption.getOrElse(empty.iterations),
          learningRate = learningRateOption.getOrElse(empty.learningRate),
          ndcgCutoff = ndcgCutoffOption.getOrElse(empty.ndcgCutoff),
          maxDepth = maxDepthOption.getOrElse(empty.maxDepth),
          seed = seedOption.getOrElse(empty.seed),
          numLeaves = numLeavesOption.getOrElse(empty.numLeaves),
          sampling = samplingOption.getOrElse(empty.sampling)
        )
      }
    )
    implicit val lgbmEncoder: Encoder[LightGBMBackend] = deriveEncoder[LightGBMBackend]
    implicit val lgbmCodec: Codec[LightGBMBackend]     = Codec.from(lgbmDecoder, lgbmEncoder)

    implicit val xgboostDecoder: Decoder[XGBoostBackend] = Decoder.instance(c =>
      for {
        iterationsOption   <- c.downField("iterations").as[Option[Int]]
        learningRateOption <- c.downField("learningRate").as[Option[Double]]
        ndcgCutoffOption   <- c.downField("ndcgCutoff").as[Option[Int]]
        maxDepthOption     <- c.downField("maxDepth").as[Option[Int]]
        seedOption         <- c.downField("seed").as[Option[Int]]
        samplingOption     <- c.downField("sampling").as[Option[Double]]
      } yield {
        val empty = XGBoostBackend()
        XGBoostBackend(
          iterations = iterationsOption.getOrElse(empty.iterations),
          learningRate = learningRateOption.getOrElse(empty.learningRate),
          ndcgCutoff = ndcgCutoffOption.getOrElse(empty.ndcgCutoff),
          maxDepth = maxDepthOption.getOrElse(empty.maxDepth),
          seed = seedOption.getOrElse(empty.seed),
          sampling = samplingOption.getOrElse(empty.sampling)
        )
      }
    )
    implicit val xgboostEncoder: Encoder[XGBoostBackend] = deriveEncoder[XGBoostBackend]
    implicit val xgboostCodec: Codec[XGBoostBackend]     = Codec.from(xgboostDecoder, xgboostEncoder)

    implicit val modelBackendEncoder: Encoder[ModelBackend] = Encoder.instance {
      case l: LightGBMBackend => lgbmEncoder(l).deepMerge(withType("lightgbm"))
      case x: XGBoostBackend  => xgboostEncoder(x).deepMerge(withType("xgboost"))
    }
    implicit val modelBackendDecoder: Decoder[ModelBackend] = Decoder.instance(c =>
      c.downField("type").as[String] match {
        case Left(err)         => Left(err)
        case Right("lightgbm") => lgbmDecoder.tryDecode(c)
        case Right("xgboost")  => xgboostDecoder.tryDecode(c)
        case Right(other)      => Left(DecodingFailure(s"cannot decode model type $other", c.history))
      }
    )
    implicit val modelBackendCodec: Codec[ModelBackend] = Codec.from(modelBackendDecoder, modelBackendEncoder)
  }
}
