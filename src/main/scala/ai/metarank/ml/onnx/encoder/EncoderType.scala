package ai.metarank.ml.onnx.encoder

import ai.metarank.ml.onnx.ModelHandle
import io.circe.generic.semiauto._
import io.circe.{Decoder, DecodingFailure}

sealed trait EncoderType

object EncoderType {
  case class BertEncoderType(
      model: ModelHandle,
      itemFieldCache: Option[String] = None,
      rankingFieldCache: Option[String] = None,
      modelFile: String = "pytorch_model.onnx",
      vocabFile: String = "vocab.txt",
      dim: Int
  ) extends EncoderType

  case class CsvEncoderType(itemFieldCache: String, rankingFieldCache: String, dim: Int) extends EncoderType

  implicit val bertDecoder: Decoder[BertEncoderType] = Decoder.instance(c =>
    for {
      model     <- c.downField("model").as[ModelHandle]
      modelFile <- c.downField("modelFile").as[Option[String]]
      vocabFile <- c.downField("vocabFile").as[Option[String]]
      itemCache <- c.downField("itemFieldCache").as[Option[String]]
      rankCache <- c.downField("rankingFieldCache").as[Option[String]]
      dim       <- c.downField("dim").as[Int]
    } yield {
      BertEncoderType(
        model,
        modelFile = modelFile.getOrElse("pytorch_model.onnx"),
        vocabFile = vocabFile.getOrElse("vocab.txt"),
        itemFieldCache = itemCache,
        rankingFieldCache = rankCache,
        dim = dim
      )
    }
  )
  implicit val bertEncoder: io.circe.Encoder[BertEncoderType] = deriveEncoder[BertEncoderType]
  implicit val csvDecoder: Decoder[CsvEncoderType]            = deriveDecoder[CsvEncoderType]
  implicit val csvEncoder: io.circe.Encoder[CsvEncoderType]   = deriveEncoder[CsvEncoderType]

  implicit val encoderTypeDecoder: Decoder[EncoderType] = Decoder.instance(c =>
    c.downField("type").as[String] match {
      case Left(err)            => Left(err)
      case Right("transformer") => bertDecoder.tryDecode(c)
      case Right("csv")         => csvDecoder.tryDecode(c)
      case Right(other)         => Left(DecodingFailure(s"cannot decode embedding type $other", c.history))
    }
  )
}
