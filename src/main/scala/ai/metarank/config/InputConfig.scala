package ai.metarank.config

import ai.metarank.source.format.JsonFormat
import cats.data.NonEmptyList
import io.circe.Decoder
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto.deriveConfiguredDecoder

import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}
import scala.concurrent.duration._
sealed trait InputConfig

object InputConfig {
  import ai.metarank.util.DurationJson._
  sealed trait SourceOffset
  object SourceOffset {
    case object Latest                                    extends SourceOffset
    case object Earliest                                  extends SourceOffset
    case class ExactTimestamp(ts: Long)                   extends SourceOffset
    case class RelativeDuration(duration: FiniteDuration) extends SourceOffset

    val tsPattern       = "ts=([0-9]+)".r
    val durationPattern = "last=([0-9]+)([smhd])".r
    implicit val sourceOffsetDecoder: Decoder[SourceOffset] = Decoder.decodeString.emapTry {
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
      format: SourceFormat = JsonFormat
  ) extends InputConfig

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
      // options: Option[Map[String, String]] = None,
      format: SourceFormat = JsonFormat
  ) extends InputConfig

  implicit val conf = Configuration.default
    .withDiscriminator("type")
    .withDefaults
    .copy(transformConstructorNames = {
      case "FileInputConfig"    => "file"
      case "KafkaInputConfig"   => "kafka"
      case "PulsarInputConfig"  => "pulsar"
      case "KinesisInputConfig" => "kinesis"
    })
  implicit val eventSourceDecoder: Decoder[InputConfig] = deriveConfiguredDecoder

}
