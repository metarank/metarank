package ai.metarank.source

import ai.metarank.config.EventSourceConfig.{PulsarSourceConfig, SourceOffset}
import ai.metarank.model.Event
import ai.metarank.util.Logging
import io.findify.featury.model.Timestamp
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.connector.pulsar.source.{PulsarSource, PulsarSourceOptions}
import org.apache.flink.connector.pulsar.source.enumerator.cursor.{StartCursor, StopCursor}
import org.apache.flink.connector.pulsar.source.reader.deserializer.PulsarDeserializationSchema
import io.findify.flink.api._
import org.apache.flink.util.Collector
import org.apache.pulsar.client.api.{Message, SubscriptionType}
import io.circe.parser._

import java.nio.charset.StandardCharsets

case class PulsarEventSource(conf: PulsarSourceConfig)(implicit ti: TypeInformation[Event]) extends EventSource {
  override def eventStream(env: StreamExecutionEnvironment, bounded: Boolean)(implicit
      ti: TypeInformation[Event]
  ): DataStream[Event] = {
    val subscription = conf.subscriptionType match {
      case "exclusive" => SubscriptionType.Exclusive
      case "shared"    => SubscriptionType.Shared
      case "failover"  => SubscriptionType.Failover
      case other       => throw new Exception(s"subscription type $other is not supported")
    }
    val cursor = conf.offset match {
      case SourceOffset.Latest                     => StartCursor.latest()
      case SourceOffset.Earliest                   => StartCursor.earliest()
      case SourceOffset.ExactTimestamp(ts)         => StartCursor.fromMessageTime(ts)
      case SourceOffset.RelativeDuration(duration) => StartCursor.fromMessageTime(Timestamp.now.minus(duration).ts)
    }
    val deserializer = new PulsarDeserializationSchema[Event] {
      override def getProducedType: TypeInformation[Event] = ti

      override def deserialize(message: Message[Array[Byte]], out: Collector[Event]): Unit = {
        val json = new String(message.getData, StandardCharsets.UTF_8)
        decode[Event](json) match {
          case Left(value) =>
            logger.error(s"cannot parse message $json", value)
            throw value
          case Right(value) =>
            out.collect(value)
        }
      }
    }

    val sourceBuilder = PulsarSource
      .builder[Event]()
      .setTopics(conf.topic)
      .setAdminUrl(conf.adminUrl)
      .setServiceUrl(conf.serviceUrl)
      .setSubscriptionName(conf.subscriptionName)
      .setSubscriptionType(subscription)
      .setStartCursor(cursor)
      .setDeserializationSchema(deserializer)
      .setConfig(PulsarSourceOptions.PULSAR_ENABLE_AUTO_ACKNOWLEDGE_MESSAGE, Boolean.box(true))
      .setProperties(customProperties(conf.options))

    val source = if (bounded) sourceBuilder.setBoundedStopCursor(StopCursor.latest()).build() else sourceBuilder.build()
    env.fromSource(source, EventWatermarkStrategy(), "pulsar-source")
  }
}
