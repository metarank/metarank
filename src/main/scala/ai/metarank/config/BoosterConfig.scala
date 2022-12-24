package ai.metarank.config

import ai.metarank.config.ModelConfig.withType
import io.circe.generic.semiauto.deriveEncoder
import io.circe.{Codec, Decoder, DecodingFailure, Encoder}

import scala.util.Random

sealed trait BoosterConfig {
  def iterations: Int
  def learningRate: Double
  def ndcgCutoff: Int
  def maxDepth: Int
  def seed: Int
  def sampling: Double
}

object BoosterConfig {
  case class LightGBMConfig(
      iterations: Int = 100,
      learningRate: Double = 0.1,
      ndcgCutoff: Int = 10,
      maxDepth: Int = 8,
      seed: Int = Random.nextInt(Int.MaxValue),
      numLeaves: Int = 16,
      sampling: Double = 0.8
  ) extends BoosterConfig

  case class XGBoostConfig(
      iterations: Int = 100,
      learningRate: Double = 0.1,
      ndcgCutoff: Int = 10,
      maxDepth: Int = 8,
      seed: Int = Random.nextInt(Int.MaxValue),
      sampling: Double = 0.8
  ) extends BoosterConfig

  implicit val lgbmDecoder: Decoder[LightGBMConfig] = Decoder.instance(c =>
    for {
      iterationsOption   <- c.downField("iterations").as[Option[Int]]
      learningRateOption <- c.downField("learningRate").as[Option[Double]]
      ndcgCutoffOption   <- c.downField("ndcgCutoff").as[Option[Int]]
      maxDepthOption     <- c.downField("maxDepth").as[Option[Int]]
      seedOption         <- c.downField("seed").as[Option[Int]]
      numLeavesOption    <- c.downField("numLeaves").as[Option[Int]]
      samplingOption     <- c.downField("sampling").as[Option[Double]]
    } yield {
      val empty = LightGBMConfig()
      LightGBMConfig(
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
  implicit val lgbmEncoder: Encoder[LightGBMConfig] = deriveEncoder[LightGBMConfig]
  implicit val lgbmCodec: Codec[LightGBMConfig]     = Codec.from(lgbmDecoder, lgbmEncoder)

  implicit val xgboostDecoder: Decoder[XGBoostConfig] = Decoder.instance(c =>
    for {
      iterationsOption   <- c.downField("iterations").as[Option[Int]]
      learningRateOption <- c.downField("learningRate").as[Option[Double]]
      ndcgCutoffOption   <- c.downField("ndcgCutoff").as[Option[Int]]
      maxDepthOption     <- c.downField("maxDepth").as[Option[Int]]
      seedOption         <- c.downField("seed").as[Option[Int]]
      samplingOption     <- c.downField("sampling").as[Option[Double]]
    } yield {
      val empty = XGBoostConfig()
      XGBoostConfig(
        iterations = iterationsOption.getOrElse(empty.iterations),
        learningRate = learningRateOption.getOrElse(empty.learningRate),
        ndcgCutoff = ndcgCutoffOption.getOrElse(empty.ndcgCutoff),
        maxDepth = maxDepthOption.getOrElse(empty.maxDepth),
        seed = seedOption.getOrElse(empty.seed),
        sampling = samplingOption.getOrElse(empty.sampling)
      )
    }
  )
  implicit val xgboostEncoder: Encoder[XGBoostConfig] = deriveEncoder[XGBoostConfig]
  implicit val xgboostCodec: Codec[XGBoostConfig]     = Codec.from(xgboostDecoder, xgboostEncoder)

  implicit val boosterConfigEncoder: Encoder[BoosterConfig] = Encoder.instance {
    case l: LightGBMConfig => lgbmEncoder(l).deepMerge(withType("lightgbm"))
    case x: XGBoostConfig  => xgboostEncoder(x).deepMerge(withType("xgboost"))
  }
  implicit val boosterConfigDecoder: Decoder[BoosterConfig] = Decoder.instance(c =>
    c.downField("type").as[String] match {
      case Left(err)         => Left(err)
      case Right("lightgbm") => lgbmDecoder.tryDecode(c)
      case Right("xgboost")  => xgboostDecoder.tryDecode(c)
      case Right(other)      => Left(DecodingFailure(s"cannot decode model type $other", c.history))
    }
  )
  implicit val boosterConfigCodec: Codec[BoosterConfig] = Codec.from(boosterConfigDecoder, boosterConfigEncoder)
}
