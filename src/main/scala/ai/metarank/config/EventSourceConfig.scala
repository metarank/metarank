package ai.metarank.config

import cats.data.NonEmptyList
import io.circe.Decoder
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto.deriveConfiguredDecoder

import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}

sealed trait EventSourceConfig

object EventSourceConfig {
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

  case class FileSourceConfig(path: MPath) extends EventSourceConfig
  case class KafkaSourceConfig(brokers: NonEmptyList[String], topic: String, groupId: String, offset: SourceOffset)
      extends EventSourceConfig
  case class PulsarSourceConfig(
      serviceUrl: String,
      adminUrl: String,
      topic: String,
      subscriptionName: String,
      subscriptionType: String,
      offset: SourceOffset
  ) extends EventSourceConfig
  case class KinesisSourceConfig(
      topic: String,
      offset: SourceOffset,
      region: String,
      options: Option[Map[String, String]] = None
  ) extends EventSourceConfig
  case class RestSourceConfig(bufferSize: Int = 10000, host: String = "localhost", port: Int = 8080)
      extends EventSourceConfig
  implicit val conf = Configuration.default
    .withDiscriminator("type")
    .withDefaults
    .copy(transformConstructorNames = {
      case "FileSourceConfig"    => "file"
      case "KafkaSourceConfig"   => "kafka"
      case "PulsarSourceConfig"  => "pulsar"
      case "RestSourceConfig"    => "rest"
      case "KinesisSourceConfig" => "kinesis"
    })
  implicit val eventSourceDecoder: Decoder[EventSourceConfig] = deriveConfiguredDecoder

}
