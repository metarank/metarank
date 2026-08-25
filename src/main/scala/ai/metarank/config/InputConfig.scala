package ai.metarank.config

import ai.metarank.config.InputConfig.FileInputConfig.SortingType
import ai.metarank.config.InputConfig.FileInputConfig.SortingType.{SortByName, SortByTime}
import ai.metarank.source.format.JsonFormat
import cats.data.NonEmptyList
import io.circe.{Decoder, DecodingFailure}

import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}
import scala.concurrent.duration.*
sealed trait InputConfig

object InputConfig {
  import ai.metarank.util.DurationJson.given
  sealed trait SourceOffset
  object SourceOffset {
    case object Latest                                    extends SourceOffset
    case object Earliest                                  extends SourceOffset
    case class ExactTimestamp(ts: Long)                   extends SourceOffset
    case class RelativeDuration(duration: FiniteDuration) extends SourceOffset

    val tsPattern       = "ts=([0-9]+)".r
    val durationPattern = "last=([0-9]+)([smhd])".r
    given sourceOffsetDecoder: Decoder[SourceOffset] = Decoder.decodeString.emapTry {
      case "earliest"                   => Success(Earliest)
      case "latest"                     => Success(Latest)
      case tsPattern(ts)                => Success(ExactTimestamp(ts.toLong))
      case durationPattern(num, suffix) => Success(RelativeDuration(FiniteDuration(num.toLong, suffix)))
      case other                        => Failure(new Exception(s"offset $other is not supported"))
    }
  }

  case class KafkaInputConfig(
      brokers: NonEmptyList[String],
      topic: String,
      groupId: String,
      offset: Option[SourceOffset],
      options: Option[Map[String, String]] = None,
      format: SourceFormat = JsonFormat
  ) extends InputConfig

  case class FileInputConfig(
      path: String,
      offset: SourceOffset = SourceOffset.Earliest,
      format: SourceFormat = JsonFormat,
      sort: SortingType = SortByName
  ) extends InputConfig

  object FileInputConfig {
    enum SortingType {
      case SortByName
      case SortByTime
    }

    given sortDecoder: Decoder[SortingType] = Decoder.decodeString.emapTry {
      case "name" => Success(SortByName)
      case "time" => Success(SortByTime)
      case other  => Failure(new IllegalAccessException(s"cannot decode sorting type $other"))
    }
  }

  case class PulsarInputConfig(
      serviceUrl: String,
      adminUrl: String,
      topic: String,
      subscriptionName: String,
      subscriptionType: String,
      offset: Option[SourceOffset] = None,
      options: Option[Map[String, String]] = None,
      format: SourceFormat = JsonFormat
  ) extends InputConfig

  case class KinesisInputConfig(
      topic: String,
      offset: SourceOffset,
      region: String,
      endpoint: Option[String] = None,
      skipCertVerification: Boolean = false,
      getRecordsPeriod: FiniteDuration = 200.millis,
      sleepOnEmptyPeriod: FiniteDuration = 1.second,
      format: SourceFormat = JsonFormat
  ) extends InputConfig

  given kafkaDecoder: Decoder[KafkaInputConfig] = Decoder.instance(c =>
    for {
      brokers <- c.downField("brokers").as[NonEmptyList[String]]
      topic   <- c.downField("topic").as[String]
      groupId <- c.downField("groupId").as[String]
      offset  <- c.downField("offset").as[Option[SourceOffset]]
      options <- c.downField("options").as[Option[Map[String, String]]]
      format  <- c.getOrElse[SourceFormat]("format")(JsonFormat)
    } yield KafkaInputConfig(
      brokers = brokers,
      topic = topic,
      groupId = groupId,
      offset = offset,
      options = options,
      format = format
    )
  )

  given fileDecoder: Decoder[FileInputConfig] = Decoder.instance(c =>
    for {
      path   <- c.downField("path").as[String]
      offset <- c.getOrElse[SourceOffset]("offset")(SourceOffset.Earliest)
      format <- c.getOrElse[SourceFormat]("format")(JsonFormat)
      sort   <- c.getOrElse[SortingType]("sort")(SortByName)
    } yield FileInputConfig(path = path, offset = offset, format = format, sort = sort)
  )

  given pulsarDecoder: Decoder[PulsarInputConfig] = Decoder.instance(c =>
    for {
      serviceUrl       <- c.downField("serviceUrl").as[String]
      adminUrl         <- c.downField("adminUrl").as[String]
      topic            <- c.downField("topic").as[String]
      subscriptionName <- c.downField("subscriptionName").as[String]
      subscriptionType <- c.downField("subscriptionType").as[String]
      offset           <- c.downField("offset").as[Option[SourceOffset]]
      options          <- c.downField("options").as[Option[Map[String, String]]]
      format           <- c.getOrElse[SourceFormat]("format")(JsonFormat)
    } yield PulsarInputConfig(
      serviceUrl = serviceUrl,
      adminUrl = adminUrl,
      topic = topic,
      subscriptionName = subscriptionName,
      subscriptionType = subscriptionType,
      offset = offset,
      options = options,
      format = format
    )
  )

  given kinesisDecoder: Decoder[KinesisInputConfig] = Decoder.instance(c =>
    for {
      topic                <- c.downField("topic").as[String]
      offset               <- c.downField("offset").as[SourceOffset]
      region               <- c.downField("region").as[String]
      endpoint             <- c.downField("endpoint").as[Option[String]]
      skipCertVerification <- c.getOrElse[Boolean]("skipCertVerification")(false)
      getRecordsPeriod     <- c.getOrElse[FiniteDuration]("getRecordsPeriod")(200.millis)
      sleepOnEmptyPeriod   <- c.getOrElse[FiniteDuration]("sleepOnEmptyPeriod")(1.second)
      format               <- c.getOrElse[SourceFormat]("format")(JsonFormat)
    } yield KinesisInputConfig(
      topic = topic,
      offset = offset,
      region = region,
      endpoint = endpoint,
      skipCertVerification = skipCertVerification,
      getRecordsPeriod = getRecordsPeriod,
      sleepOnEmptyPeriod = sleepOnEmptyPeriod,
      format = format
    )
  )

  given eventSourceDecoder: Decoder[InputConfig] = Decoder.instance(c =>
    c.downField("type").as[String] match {
      case Left(_)          => Left(DecodingFailure("required field 'type' missing in input config", c.history))
      case Right("file")    => fileDecoder.tryDecode(c)
      case Right("kafka")   => kafkaDecoder.tryDecode(c)
      case Right("pulsar")  => pulsarDecoder.tryDecode(c)
      case Right("kinesis") => kinesisDecoder.tryDecode(c)
      case Right(other)     => Left(DecodingFailure(s"input type '$other' is not supported", c.history))
    }
  )

}
