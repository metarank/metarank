package ai.metarank.ml.onnx.encoder

import ai.metarank.ml.onnx.ModelHandle
import io.circe.generic.semiauto._
import io.circe.{Decoder, DecodingFailure}

sealed trait EncoderConfig

object EncoderConfig {
  case class CrossEncoderConfig(
      model: Option[ModelHandle],
      cache: Option[String] = None,
      modelFile: String = "pytorch_model.onnx",
      vocabFile: String = "vocab.txt",
      dim: Int
  ) extends EncoderConfig

  implicit val crossDecoder: Decoder[CrossEncoderConfig] = Decoder.instance(c =>
    for {
      model     <- c.downField("model").as[Option[ModelHandle]]
      modelFile <- c.downField("modelFile").as[Option[String]]
      vocabFile <- c.downField("vocabFile").as[Option[String]]
      cache     <- c.downField("cache").as[Option[String]]
      dim       <- c.downField("dim").as[Int]
      _ <- (model, cache) match {
        case (None, None) =>
          Left(DecodingFailure("either 'model' or 'cache' fields should be present for cross-encoder", c.history))
        case _ => Right({})
      }
    } yield {
      CrossEncoderConfig(
        model,
        modelFile = modelFile.getOrElse("pytorch_model.onnx"),
        vocabFile = vocabFile.getOrElse("vocab.txt"),
        cache = cache,
        dim = dim
      )
    }
  )

  implicit val crossEncoder: io.circe.Encoder[CrossEncoderConfig] = deriveEncoder

  case class BiEncoderConfig(
      model: Option[ModelHandle],
      itemFieldCache: Option[String] = None,
      rankingFieldCache: Option[String] = None,
      modelFile: String = "pytorch_model.onnx",
      vocabFile: String = "vocab.txt",
      dim: Int
  ) extends EncoderConfig

  implicit val biencDecoder: Decoder[BiEncoderConfig] = Decoder.instance(c =>
    for {
      model     <- c.downField("model").as[Option[ModelHandle]]
      modelFile <- c.downField("modelFile").as[Option[String]]
      vocabFile <- c.downField("vocabFile").as[Option[String]]
      itemCache <- c.downField("itemFieldCache").as[Option[String]]
      rankCache <- c.downField("rankingFieldCache").as[Option[String]]
      dim <- c.downField("dim").as[Int]
      _ <- (model, itemCache, rankCache) match {
        case (None, None, None) =>
          Left(
            DecodingFailure("one of model/itemFieldCache/rankingFieldCache should be present for bi-encoder", c.history)
          )
        case _ => Right({})
      }
    } yield {
      BiEncoderConfig(
        model,
        modelFile = modelFile.getOrElse("pytorch_model.onnx"),
        vocabFile = vocabFile.getOrElse("vocab.txt"),
        itemFieldCache = itemCache,
        rankingFieldCache = rankCache,
        dim = dim
      )
    }
  )
  implicit val bertEncoder: io.circe.Encoder[BiEncoderConfig] = deriveEncoder[BiEncoderConfig]

  implicit val encoderTypeDecoder: Decoder[EncoderConfig] = Decoder.instance(c =>
    c.downField("type").as[String] match {
      case Left(err)              => Left(err)
      case Right("bi-encoder")    => biencDecoder.tryDecode(c)
      case Right("cross-encoder") => crossDecoder.tryDecode(c)
      case Right(other)           => Left(DecodingFailure(s"cannot decode embedding type $other", c.history))
    }
  )
}
