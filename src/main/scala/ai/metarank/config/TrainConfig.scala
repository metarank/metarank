package ai.metarank.config

import ai.metarank.config.StateStoreConfig.RedisCredentials
import ai.metarank.config.StateStoreConfig.RedisStateConfig.{CacheConfig, DBConfig, PipelineConfig}
import ai.metarank.fstore.redis.codec.StoreFormat
import ai.metarank.fstore.redis.codec.StoreFormat.BinaryStoreFormat
import io.circe.generic.semiauto.deriveDecoder
import io.circe.{Decoder, DecodingFailure}

sealed trait TrainConfig

object TrainConfig {
  case class S3TrainConfig(bucket: String, prefix: String) extends TrainConfig
  implicit val s3decoder: Decoder[S3TrainConfig] = deriveDecoder[S3TrainConfig]

  case class FileTrainConfig(path: String) extends TrainConfig
  implicit val fileDecoder: Decoder[FileTrainConfig] = deriveDecoder[FileTrainConfig]

  case class RedisTrainConfig(
      host: Hostname,
      port: Port,
      db: Int = 2,
      cache: CacheConfig = CacheConfig(),
      pipeline: PipelineConfig = PipelineConfig(),
      format: StoreFormat = BinaryStoreFormat,
      auth: Option[RedisCredentials] = None
  ) extends TrainConfig
  implicit val redisDecoder: Decoder[RedisTrainConfig] = Decoder.instance(c =>
    for {
      host   <- c.downField("host").as[Hostname]
      port   <- c.downField("port").as[Port]
      db     <- c.downField("db").as[Option[Int]]
      cache  <- c.downField("cache").as[Option[CacheConfig]]
      pipe   <- c.downField("pipeline").as[Option[PipelineConfig]]
      format <- c.downField("format").as[Option[StoreFormat]]
      auth   <- c.downField("auth").as[Option[RedisCredentials]]
    } yield {
      RedisTrainConfig(
        host = host,
        port = port,
        db = db.getOrElse(2),
        cache = cache.getOrElse(CacheConfig()),
        pipeline = pipe.getOrElse(PipelineConfig()),
        format = format.getOrElse(BinaryStoreFormat),
        auth = auth
      )
    }
  )

  case class MemoryTrainConfig() extends TrainConfig
  implicit val memDecoder: Decoder[MemoryTrainConfig] = Decoder.instance(_ => Right(MemoryTrainConfig()))

  implicit val trainDecoder: Decoder[TrainConfig] = Decoder.instance(c =>
    c.downField("type").as[String] match {
      case Left(err)       => Left(err)
      case Right("s3")     => s3decoder.tryDecode(c)
      case Right("redis")  => redisDecoder.tryDecode(c)
      case Right("memory") => memDecoder.tryDecode(c)
      case Right("file")   => fileDecoder.tryDecode(c)
      case Right(other)    => Left(DecodingFailure(s"type $other is not yet supported", c.history))
    }
  )

  def fromState(conf: StateStoreConfig) = conf match {
    case StateStoreConfig.RedisStateConfig(host, port, db, cache, pipeline, format, auth) =>
      RedisTrainConfig(host, port, db.values, cache, pipeline, format, auth)
    case StateStoreConfig.MemoryStateConfig() =>
      MemoryTrainConfig()
  }
}
