package ai.metarank.config

import io.circe.generic.semiauto.deriveDecoder
import io.circe.{Decoder, DecodingFailure}

trait TrainConfig

object TrainConfig {
  case class S3TrainConfig(bucket: String, prefix: String) extends TrainConfig
  implicit val s3decoder: Decoder[S3TrainConfig] = deriveDecoder[S3TrainConfig]

  case class FileTrainConfig(path: String) extends TrainConfig
  implicit val fileDecoder: Decoder[FileTrainConfig] = deriveDecoder[FileTrainConfig]

  implicit val trainDecoder: Decoder[TrainConfig] = Decoder.instance(c =>
    c.downField("type").as[String] match {
      case Left(err)       => Left(err)
      case Right("s3")     => s3decoder.tryDecode(c)
      case Right("redis")  => StateStoreConfig.redisConfigDecoder.tryDecode(c)
      case Right("memory") => StateStoreConfig.memConfigDecoder.tryDecode(c)
      case Right("file")   => fileDecoder.tryDecode(c)
      case Right(other)    => Left(DecodingFailure(s"type $other is not yet supported", c.history))
    }
  )
}
