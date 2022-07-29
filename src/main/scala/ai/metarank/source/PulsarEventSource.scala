package ai.metarank.source

import ai.metarank.config.InputConfig.{PulsarInputConfig, SourceOffset}
import ai.metarank.config.SourceFormat
import ai.metarank.model.Event
import ai.metarank.util.Logging
import cats.effect.IO
import io.findify.featury.model.Timestamp
import org.apache.flink.api.common.typeinfo.TypeInformation
import io.findify.flink.api._
import org.apache.flink.util.Collector
import org.apache.pulsar.client.api.{Message, PulsarClient, SubscriptionType}
import io.circe.parser._

import java.nio.charset.StandardCharsets

case class PulsarEventSource(conf: PulsarInputConfig)(implicit ti: TypeInformation[Event]) extends EventSource {
  override def stream: fs2.Stream[IO, Event] = ???
//  override def eventStream(env: StreamExecutionEnvironment, bounded: Boolean)(implicit
//      ti: TypeInformation[Event]
//  ): DataStream[Event] = {
//    val subscription = conf.subscriptionType match {
//      case "exclusive" => SubscriptionType.Exclusive
//      case "shared"    => SubscriptionType.Shared
//      case "failover"  => SubscriptionType.Failover
//      case other       => throw new Exception(s"subscription type $other is not supported")
//    }
//    val cursor = conf.offset match {
//      case SourceOffset.Latest                     => StartCursor.latest()
//      case SourceOffset.Earliest                   => StartCursor.earliest()
//      case SourceOffset.ExactTimestamp(ts)         => StartCursor.fromMessageTime(ts)
//      case SourceOffset.RelativeDuration(duration) => StartCursor.fromMessageTime(Timestamp.now.minus(duration).ts)
//    }
//
//    val sourceBuilder = PulsarSource
//      .builder[Event]()
//      .setTopics(conf.topic)
//      .setAdminUrl(conf.adminUrl)
//      .setServiceUrl(conf.serviceUrl)
//      .setSubscriptionName(conf.subscriptionName)
//      .setSubscriptionType(subscription)
//      .setStartCursor(cursor)
//      .setDeserializationSchema(EventDeserializationSchema(conf.format, ti))
//      .setConfig(PulsarSourceOptions.PULSAR_ENABLE_AUTO_ACKNOWLEDGE_MESSAGE, Boolean.box(true))
//      .setProperties(customProperties(conf.options))
//
//    val source = if (bounded) sourceBuilder.setBoundedStopCursor(StopCursor.latest()).build() else sourceBuilder.build()
//    env.fromSource(source, EventWatermarkStrategy(), "pulsar-source")
//  }
}

object PulsarEventSource {
  case class Consumer(client: PulsarClient) {
    def close() = IO.fromCompletableFuture(IO(client.closeAsync()))
  }

  object Consumer {}
}
