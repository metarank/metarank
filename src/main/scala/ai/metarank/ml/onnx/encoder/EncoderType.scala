package ai.metarank.ml.onnx.encoder

import io.circe.generic.semiauto._
import io.circe.{Decoder, DecodingFailure}

sealed trait EncoderType

object EncoderType {
  case class BertEncoderType(model: String) extends EncoderType

  case class CsvEncoderType(path: String) extends EncoderType

  implicit val bertDecoder: Decoder[BertEncoderType] = deriveDecoder[BertEncoderType]
  implicit val csvDecoder: Decoder[CsvEncoderType] = deriveDecoder[CsvEncoderType]
  implicit val encoderDecoder: Decoder[EncoderType] = Decoder.instance(c =>
    c.downField("type").as[String] match {
      case Left(err) => Left(err)
      case Right("bert") => bertDecoder.tryDecode(c)
      case Right("csv") => csvDecoder.tryDecode(c)
      case Right(other) => Left(DecodingFailure(s"cannot decode embedding type $other", c.history))
    }
  )
}
