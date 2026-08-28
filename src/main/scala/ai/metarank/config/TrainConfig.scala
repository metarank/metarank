package ai.metarank.config

import ai.metarank.config.StateStoreConfig.{RedisCredentials, RedisTLS, RedisTimeouts}
import ai.metarank.config.StateStoreConfig.RedisStateConfig.{CacheConfig, PipelineConfig}
import ai.metarank.config.TrainConfig.CompressionType.{GzipCompressionType, NoCompressionType, ZstdCompressionType}
import ai.metarank.fstore.codec.StoreFormat
import ai.metarank.fstore.codec.StoreFormat.BinaryStoreFormat
import io.circe.{Decoder, DecodingFailure, Encoder, Json}

import scala.concurrent.duration.*
import scala.util.{Failure, Success}

sealed trait TrainConfig

object TrainConfig {
  import ai.metarank.util.DurationJson.given

  enum CompressionType(val ext: String) {
    case GzipCompressionType extends CompressionType(".gz")
    case ZstdCompressionType extends CompressionType(".zst")
    case NoCompressionType   extends CompressionType(".bin")
  }
  object CompressionType {
    def fromKey(key: String): Option[CompressionType] = CompressionType.values.find(c => key.endsWith(c.ext))
  }

  given compressEncoder: Encoder[CompressionType] = Encoder.instance {
    case CompressionType.GzipCompressionType => Json.fromString("gzip")
    case CompressionType.ZstdCompressionType => Json.fromString("zstd")
    case CompressionType.NoCompressionType   => Json.fromString("none")
  }

  given compressDecoder: Decoder[CompressionType] = Decoder.decodeString.emapTry {
    case "gzip" | "gz"  => Success(GzipCompressionType)
    case "zstd" | "zst" => Success(ZstdCompressionType)
    case "none"         => Success(NoCompressionType)
    case other          => Failure(new Exception(s"compression type $other not supported. Try gzip/zstd/none."))
  }

  case class S3TrainConfig(
      awsKey: Option[String] = None,
      awsKeySecret: Option[String] = None,
      bucket: String,
      prefix: String,
      region: String,
      compress: CompressionType = GzipCompressionType,
      partSizeBytes: Long = 10 * 1024 * 1024,
      partSizeEvents: Int = 1024,
      partInterval: FiniteDuration = 1.hour,
      endpoint: Option[String] = None,
      format: StoreFormat = BinaryStoreFormat,
      deduplicate: Boolean = false
  ) extends TrainConfig
  given s3TrainConfigDecoder: Decoder[S3TrainConfig] = Decoder.instance(c =>
    for {
      key             <- c.downField("key").as[Option[String]]
      secret          <- c.downField("secret").as[Option[String]]
      bucket          <- c.downField("bucket").as[String]
      prefix          <- c.downField("prefix").as[String]
      region          <- c.downField("region").as[String]
      compress        <- c.downField("compress").as[Option[CompressionType]]
      batchSizeBytes  <- c.downField("batchSizeBytes").as[Option[Long]]
      batchSizeEvents <- c.downField("batchSizeEvents").as[Option[Int]]
      partInterval    <- c.downField("partInterval").as[Option[FiniteDuration]]
      endpoint        <- c.downField("endpoint").as[Option[String]]
      format          <- c.downField("format").as[Option[StoreFormat]]
      deduplicate     <- c.downField("deduplicate").as[Option[Boolean]]
    } yield {
      S3TrainConfig(
        key,
        secret,
        bucket,
        prefix,
        region,
        compress.getOrElse(GzipCompressionType),
        batchSizeBytes.getOrElse(1024 * 1024L),
        batchSizeEvents.getOrElse(1024),
        partInterval.getOrElse(1.hour),
        endpoint,
        format.getOrElse(BinaryStoreFormat),
        deduplicate.getOrElse(false)
      )
    }
  )

  case class FileTrainConfig(
      path: String,
      format: StoreFormat = BinaryStoreFormat
  ) extends TrainConfig
  given fileDecoder: Decoder[FileTrainConfig] = Decoder.instance(c =>
    for {
      path   <- c.downField("path").as[String]
      format <- c.downField("format").as[StoreFormat]
    } yield {
      FileTrainConfig(path, format)
    }
  )

  case class DiscardTrainConfig() extends TrainConfig
  given discardDecoder: Decoder[DiscardTrainConfig] = Decoder.instance(_ => Right(DiscardTrainConfig()))

  case class RedisTrainConfig(
      host: Hostname,
      port: Port,
      db: Int = 2,
      cache: CacheConfig = CacheConfig(),
      pipeline: PipelineConfig = PipelineConfig(),
      format: StoreFormat = BinaryStoreFormat,
      auth: Option[RedisCredentials] = None,
      tls: Option[RedisTLS] = None,
      timeout: RedisTimeouts = RedisTimeouts(),
      ttl: FiniteDuration = 365.days
  ) extends TrainConfig
  given redisDecoder: Decoder[RedisTrainConfig] = Decoder.instance(c =>
    for {
      host    <- c.downField("host").as[Hostname]
      port    <- c.downField("port").as[Port]
      db      <- c.downField("db").as[Option[Int]]
      cache   <- c.downField("cache").as[Option[CacheConfig]]
      pipe    <- c.downField("pipeline").as[Option[PipelineConfig]]
      format  <- c.downField("format").as[Option[StoreFormat]]
      auth    <- c.downField("auth").as[Option[RedisCredentials]]
      tls     <- c.downField("tls").as[Option[RedisTLS]]
      timeout <- c.downField("timeout").as[Option[RedisTimeouts]].map(_.getOrElse(RedisTimeouts()))
      ttl     <- c.downField("ttl").as[Option[FiniteDuration]]
    } yield {
      RedisTrainConfig(
        host = host,
        port = port,
        db = db.getOrElse(2),
        cache = cache.getOrElse(CacheConfig()),
        pipeline = pipe.getOrElse(PipelineConfig()),
        format = format.getOrElse(BinaryStoreFormat),
        auth = auth,
        tls = tls,
        timeout = timeout,
        ttl = ttl.getOrElse(365.days)
      )
    }
  )

  case class MemoryTrainConfig() extends TrainConfig
  given memDecoder: Decoder[MemoryTrainConfig] = Decoder.instance(_ => Right(MemoryTrainConfig()))

  given trainDecoder: Decoder[TrainConfig] = Decoder.instance(c =>
    c.downField("type").as[String] match {
      case Left(err)        => Left(err)
      case Right("redis")   => redisDecoder.tryDecode(c)
      case Right("memory")  => memDecoder.tryDecode(c)
      case Right("file")    => fileDecoder.tryDecode(c)
      case Right("discard") => discardDecoder.tryDecode(c)
      case Right("s3")      => s3TrainConfigDecoder.tryDecode(c)
      case Right(other)     => Left(DecodingFailure(s"type $other is not yet supported", c.history))
    }
  )

  def fromState(conf: StateStoreConfig) = conf match {
    case StateStoreConfig.FileStateConfig(path, format, backend) => FileTrainConfig(path, format)
    case StateStoreConfig.RedisStateConfig(host, port, db, cache, pipeline, format, auth, tls, timeout) =>
      RedisTrainConfig(host, port, db.rankings, cache, pipeline, format, auth, tls, timeout)
    case StateStoreConfig.MemoryStateConfig() =>
      MemoryTrainConfig()
  }
}
